/**
 * TestIssuance.java
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) Wouter Lueks, Radboud University Nijmegen, August 2012.
 */

package org.ru.irma.api.tests.idemix;

import static org.junit.Assert.*;

import java.util.List;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;

import net.sourceforge.scuba.smartcards.CardService;
import net.sourceforge.scuba.smartcards.CardServiceException;
import net.sourceforge.scuba.smartcards.IResponseAPDU;
import net.sourceforge.scuba.smartcards.TerminalCardService;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import service.IdemixService;
import service.IdemixSmartcard;
import service.ProtocolCommand;
import service.ProtocolResponses;

import com.ibm.zurich.idmx.dm.Values;
import com.ibm.zurich.idmx.issuance.IssuanceSpec;
import com.ibm.zurich.idmx.issuance.Issuer;
import com.ibm.zurich.idmx.issuance.Message;
import com.ibm.zurich.idmx.key.IssuerKeyPair;
import com.ibm.zurich.idmx.key.IssuerPrivateKey;
import com.ibm.zurich.idmx.utils.SystemParameters;
import java.lang.reflect.Field;
import java.math.BigInteger;

import credentials.Attributes;
import credentials.CredentialsException;
import credentials.idemix.IdemixCredentials;
import credentials.idemix.IdemixPrivateKey;
import credentials.idemix.spec.IdemixIssueSpecification;

public class TestIssuance {
	
	@BeforeClass
	public static void trySetMasterSecret() throws CardException, CardServiceException {
		CardService cs = TestSetup.getCardService();
		
		IdemixService service = new IdemixService(cs, TestSetup.CRED_NR);
		
		try {
			service.open();
			service.generateMasterSecret();
			service.close();
		} catch (CardServiceException e) {
			System.out.println("Setting master secret failed, this is normal except for the first run.");
		}
	}
	
	@Before
	public void setupIdemixLibrary() {
    	TestSetup.setupSystem();
    	TestSetup.setupCredentialStructure();
	}

	
	@Test
	public void issueCredentialWithoutAPI() {
    	IssuanceSpec issuanceSpec = TestSetup.setupIssuanceSpec();
    	
		Values values = getIssuanceValues(issuanceSpec.getPublicKey()
				.getGroupParams().getSystemParams());
		
		IssuerPrivateKey isk = TestSetup.setupIssuerPrivateKey();

        // run the issuance protocol.
        Issuer issuer = new Issuer(new IssuerKeyPair(isk), issuanceSpec, null, null, values);

        IdemixService recipient = null;
        try {
            CardTerminal terminal = TerminalFactory.getDefault().terminals().list().get(0);            
            recipient = new IdemixService(new TerminalCardService(terminal),TestSetup.CRED_NR);
            recipient.open();
            recipient.sendPin(TestSetup.DEFAULT_PIN);
            recipient.setIssuanceSpecification(issuanceSpec);
            recipient.setAttributes(issuanceSpec, values);
        } catch (Exception e) {
            fail(e.getMessage()); 
            e.printStackTrace();            
        }
         
        Message msgToRecipient1 = issuer.round0();
        if (msgToRecipient1 == null) {
            fail("round0");
        }

        Message msgToIssuer1 = recipient.round1(msgToRecipient1);
        if (msgToIssuer1 == null) {
            fail("round1");
        }

        Message msgToRecipient2 = issuer.round2(msgToIssuer1);
        if (msgToRecipient2 == null) {
            fail("round2");
        }

        recipient.round3(msgToRecipient2);
	}
	
	@Test
	public void issueCredentialWithCardService() throws CardException, CredentialsException {
		IdemixIssueSpecification spec = IdemixIssueSpecification
				.fromIdemixIssuanceSpec(
						TestSetup.ISSUER_PK_LOCATION,
						TestSetup.CRED_STRUCT_ID,
						(short) (TestSetup.CRED_NR + 1));

		IdemixPrivateKey isk = IdemixPrivateKey.fromIdemixPrivateKey(TestSetup.ISSUER_SK_LOCATION);

		CardService cs = TestSetup.getCardService();
		IdemixCredentials ic = new IdemixCredentials(cs);
		
		Attributes attributes = getIssuanceAttributes();

		ic.issue(spec, isk, attributes);
	}
	
	@Test
	public void issueCredentialAsync() throws CardException,
			CredentialsException, CardServiceException {
		IdemixIssueSpecification spec = IdemixIssueSpecification
				.fromIdemixIssuanceSpec(
						TestSetup.ISSUER_PK_LOCATION,
						TestSetup.CRED_STRUCT_ID,
						(short) (TestSetup.CRED_NR + 2));

		IdemixPrivateKey isk = new IdemixPrivateKey(TestSetup.setupIssuerPrivateKey());

		Attributes attributes = getIssuanceAttributes();
		IdemixCredentials ic = new IdemixCredentials();
		
		// Initialize the issuer
		Issuer issuer = new Issuer(isk.getIssuerKeyPair(), spec.getIssuanceSpec(),
				null, null, spec.getValues(attributes));
		
		// Handling service here as we need to maintain connection.
		CardService service = TestSetup.getCardService();
		service.open();

		List<ProtocolCommand> commands = ic.requestIssueRound1Commands(spec, attributes, issuer);
		commands.add(0, IdemixSmartcard.selectAppletCommand);
		commands.add(1, IdemixSmartcard.sendPinCommand(TestSetup.DEFAULT_PIN));

		ProtocolResponses responses = executeCommands(commands, service);
		commands = ic.requestIssueRound3Commands(spec, attributes, issuer, responses);
		responses = executeCommands(commands, service);

		service.close();
		// Note: no processing of the commands is necessary generally, as long
		// as errors propagate back up the change
	}

	private BigInteger nonce1;

	@Test
	public void issueCredentialAsyncSplit() throws CardException,
			CredentialsException, CardServiceException {
		// Handling service here as we need to maintain connection.
		CardService service = TestSetup.getCardService();
		service.open();

		List<ProtocolCommand> commands = issueCredentialAsyncPart1(service);

		ProtocolResponses responses = executeCommands(commands, service);

		responses = issueCredentialAsyncPart2(service, responses);

		service.close();
		// Note: no processing of the commands is necessary generally, as long
		// as errors propagate back up the change
	}

	private List<ProtocolCommand> issueCredentialAsyncPart1(CardService service)
			throws CredentialsException, CardServiceException {
		IdemixIssueSpecification spec = IdemixIssueSpecification
				.fromIdemixIssuanceSpec(
						TestSetup.ISSUER_PK_LOCATION,
						TestSetup.CRED_STRUCT_ID,
						(short) (TestSetup.CRED_NR + 3));

		IdemixPrivateKey isk = new IdemixPrivateKey(TestSetup.setupIssuerPrivateKey());

		Attributes attributes = getIssuanceAttributes();
		IdemixCredentials ic = new IdemixCredentials();

		// Initialize the issuer
		Issuer issuer = new Issuer(isk.getIssuerKeyPair(), spec.getIssuanceSpec(),
				null, null, spec.getValues(attributes));

		// Run part one of protocol
		List<ProtocolCommand> commands = ic.requestIssueRound1Commands(spec, attributes, issuer);
		commands.add(0, IdemixSmartcard.selectAppletCommand);
		commands.add(1, IdemixSmartcard.sendPinCommand(TestSetup.DEFAULT_PIN));

		// Save state, this is the nasty part
		try {
			Field nonce1Field = Issuer.class.getDeclaredField("nonce1");
			nonce1Field.setAccessible(true);
			nonce1 = (BigInteger) nonce1Field.get(issuer);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return commands;
	}

	private ProtocolResponses issueCredentialAsyncPart2(CardService service,
			ProtocolResponses responses) throws CredentialsException,
			CardServiceException {
		// Setup for next part
		IdemixIssueSpecification spec = IdemixIssueSpecification
				.fromIdemixIssuanceSpec(
						TestSetup.ISSUER_PK_LOCATION,
						TestSetup.CRED_STRUCT_ID,
						(short) (TestSetup.CRED_NR + 3));

		IdemixPrivateKey isk = new IdemixPrivateKey(TestSetup.setupIssuerPrivateKey());

		Attributes attributes = getIssuanceAttributes();
		IdemixCredentials ic = new IdemixCredentials();

		// Initialize the issuer
		Issuer issuer = new Issuer(isk.getIssuerKeyPair(), spec.getIssuanceSpec(),
				null, null, spec.getValues(attributes));

		// Restore the state, this is the nasty part
		try {
			Field nonce1Field = Issuer.class.getDeclaredField("nonce1");
			nonce1Field.setAccessible(true);
			nonce1Field.set(issuer, nonce1);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Run next part of protocol
		List<ProtocolCommand> commands = ic.requestIssueRound3Commands(spec, attributes, issuer, responses);
		responses = executeCommands(commands, service);

		return responses;
	}

    private Values getIssuanceValues(SystemParameters syspars) {
        Values values = new Values(syspars);
        values.add("attr1", TestSetup.ATTRIBUTE_VALUE_1);
        values.add("attr2", TestSetup.ATTRIBUTE_VALUE_2);
        values.add("attr3", TestSetup.ATTRIBUTE_VALUE_3);
        values.add("attr4", TestSetup.ATTRIBUTE_VALUE_4);
        
        return values;
    }
    
    /**
     * TODO: Actually, I do not know how to make ByteEncoded attributes containing the
     * BigNumbers usually used for Idemix... Or maybe this works naturally. At least needs
     * checking.
     * 
     * @param syspars
     * @return
     */
    private Attributes getIssuanceAttributes() {
        // Return the attributes that have been revealed during the proof
        Attributes attributes = new Attributes();

        attributes.add("attr1", TestSetup.ATTRIBUTE_VALUE_1.toByteArray());
        attributes.add("attr2", TestSetup.ATTRIBUTE_VALUE_2.toByteArray());
        attributes.add("attr3", TestSetup.ATTRIBUTE_VALUE_3.toByteArray());
        attributes.add("attr4", TestSetup.ATTRIBUTE_VALUE_4.toByteArray());
        
        return attributes;
    }

	private ProtocolResponses executeCommands(List<ProtocolCommand> commands,
			CardService service) throws CardServiceException {
		ProtocolResponses responses = new ProtocolResponses();
		for (ProtocolCommand c : commands) {
			IResponseAPDU response = IdemixService.transmit(service, c.command);
			responses.put(c.key, response);
			if (response.getSW() != 0x00009000) {
				// don't bother with the rest of the commands...
				// TODO: get error message from global table
				String errorMessage = c.errorMap != null
						&& c.errorMap.containsKey(response.getSW()) ? c.errorMap
						.get(response.getSW()) : "";
				throw new CardServiceException(String.format(
						"Command failed: \"%s\", SW: %04x (%s)", c.description,
						response.getSW(), errorMessage));
			}
		}

		return responses;
	}
}
