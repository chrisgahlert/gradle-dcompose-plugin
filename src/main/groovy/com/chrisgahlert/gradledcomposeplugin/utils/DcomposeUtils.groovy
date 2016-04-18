package com.chrisgahlert.gradledcomposeplugin.utils

import java.security.MessageDigest

/**
 * Created by chris on 18.04.16.
 */
class DcomposeUtils {
    static String sha1Hash(String source) {
        def sha1 = MessageDigest.getInstance("SHA1")
        def digest = sha1.digest(source.bytes)
        def hash = new BigInteger(1, digest).toString(16)
        hash
    }
}
