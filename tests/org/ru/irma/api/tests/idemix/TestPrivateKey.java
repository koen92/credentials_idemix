/**
 * TestPrivateKey.java
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

package org.ru.irma.api.tests.idemix;

import org.junit.Test;

import credentials.idemix.IdemixPrivateKey;
import static org.junit.Assert.*;

public class TestPrivateKey {
	/**
	 * Verify Idemix-library behavior in case of missing or not yet 
	 * loaded public key. Assuming the library will set the public key
	 * to null in this case.
	 */
	@Test
	public void testMissingPublicKey() {
		IdemixPrivateKey isk = IdemixPrivateKey
				.fromIdemixPrivateKey(TestSetup.ISSUER_SK_LOCATION);
		
		assertNull(isk.getPrivateKey().getPublicKey());
	}
}
