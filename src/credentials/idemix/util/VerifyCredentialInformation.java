/**
 * VerifyCredentialInformation.java
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
 * Copyright (C) Wouter Lueks, Radboud University Nijmegen, September 2012.
 */

package credentials.idemix.util;

import java.net.URI;

import credentials.idemix.spec.IdemixVerifySpecification;

public class VerifyCredentialInformation extends CredentialInformation {
	private URI proofSpecLocation;
	private URI verifierBaseLocation;
	
	public VerifyCredentialInformation(String issuer, String credName,
			String verifier, String verifySpecName) {
		super(issuer, credName);
	
		verifierBaseLocation = CORE_LOCATION.resolve(verifier + "/");
		proofSpecLocation = verifierBaseLocation.resolve("Verifies/"
				+ verifySpecName + "/specification.xml");
	}
	
	public IdemixVerifySpecification getIdemixVerifySpecification() {
		return IdemixVerifySpecification.fromIdemixProofSpec(proofSpecLocation, credNr);
	}
}
