package org.irmacard.credentials.idemix.util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;

public class Crypto {
	public static final BigInteger TWO = new BigInteger("2");

	/**
	 * Creates a random integer in the range [-2^bitlength + 1, 2^bitlength - 1]
	 *
	 * TODO: Check random number generator
	 *
	 * @param bitlength		the bitlength of the resulting integer
	 * @return				a random signed integer in the given range
	 */
	public static BigInteger randomSignedInteger(int bitlength) {
		Random rnd = new Random();

		BigInteger maximum = TWO.pow(bitlength).subtract(BigInteger.ONE);
		BigInteger unsigned_maximum = maximum.multiply(TWO);

		BigInteger attempt = unsigned_maximum.add(BigInteger.ONE);
		while (attempt.compareTo(unsigned_maximum) > 0) {
			attempt = new BigInteger(bitlength + 1, rnd);
		}
		return attempt.subtract(maximum);
	}

	/**
	 * Computes the ASN.1 encoding (as used in IRMA) of a sequence of BigIntegers.
	 *
	 * Note that the number of elements is added as a first number in the sequence.
	 *
	 * @param values	The BigIntegers to include inthe ASN.1 encoding
	 */
	public static byte[] asn1Encode(BigInteger... values) {
		ASN1EncodableVector vector = new ASN1EncodableVector();

		// Store the number of values in the sequence too
		vector.add(new ASN1Integer(values.length));

		for(BigInteger value : values) {
			vector.add(new ASN1Integer(value));
		}

		DERSequence seq = new DERSequence(vector);
		byte[] encoding = null;

		try {
			encoding = seq.getEncoded();
		} catch (IOException e) {
			// IOException indicates encoding failure, this should never happen;
			e.printStackTrace();
			throw new RuntimeException("DER encoding failed");
		}

		return encoding;
	}

	/**
	 * The BigInteger representation of the SHA-256 hash of a byte array. The integer
	 * is always positive.
	 *
	 * @param input		A byte array of data to be hashed
	 * @return			The unsigned integer representing the hash value
	 */
	public static BigInteger sha256Hash(byte[] input) {
		byte[] hash = null;
		try {
			hash = MessageDigest.getInstance("SHA-256").digest(input);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new RuntimeException("Algorithm SHA-256 not found");
		}

		// Interpret the value as a _positive_ integer
		return new BigInteger(1, hash);
	}

	/**
	 * Returns a BigInteger in the range [2^start, 2^start + 2^length) that is
	 * probably prime. The probability that the number is not prime is no more
	 * than 2^(-100).
	 *
	 * TODO: Make sure this code is correct
	 *
	 * @param start_in_bits
	 *            The start of the interval (in bits)
	 * @param length_in_bits
	 *            The length of the interval (non-inclusive) (in bits)
	 * @return A number in the given range that is probably prime
	 */
	public static BigInteger probablyPrimeInBitRange(int start_in_bits, int length_in_bits) {
		Random rnd = new Random();
		BigInteger start = TWO.pow(start_in_bits); // FIXME: check
		BigInteger end = TWO.pow(start_in_bits).add(TWO.pow(length_in_bits));
		BigInteger prime = end;

		// Ensure that the generated prime is never too big
		while (prime.compareTo(end) >= 0) {
			BigInteger offset = new BigInteger(length_in_bits, rnd);
			prime = start.add(offset).nextProbablePrime();
		}

		return prime;
	}

	/**
	 * A representation of the given exponents in terms of the given bases. For
	 * given bases bases[1],...,bases[k]; exponents exps[1],...,exps[k] and
	 * modulus this function returns bases[k]^{exps[1]}*...*bases[k]^{exps[k]}
	 * (mod modulus)
	 *
	 * @param bases		bases to represent exponents in
	 * @param exps		exponents to represent
	 * @param modulus	the modulus
	 * @return			representation of the exponents in terms of the bases
	 */
	public static BigInteger representToBases(List<BigInteger> bases,
			List<BigInteger> exps, BigInteger modulus) {

		if (bases.size() < exps.size()) {
			throw new RuntimeException("Not enough bases to represent exponents");
		}

		BigInteger r = BigInteger.ONE;
		BigInteger tmp;
		for (int i = 0; i < exps.size(); i++) {
			// tmp = bases_i ^ exps_i (mod modulus)
			tmp = bases.get(i).modPow(exps.get(i), modulus);

			// r = r * tmp (mod modulus)
			r = r.multiply(tmp).mod(modulus);
		}
		return r;
	}
}