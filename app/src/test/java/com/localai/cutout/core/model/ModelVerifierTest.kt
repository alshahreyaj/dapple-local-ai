package com.localai.cutout.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ModelVerifierTest {
    @Test fun computesSha256ForFile() {
        val file = File.createTempFile("model", ".part")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ModelVerifier.sha256(file))
        } finally { file.delete() }
    }
    @Test fun rejectsWrongSizeFileBeforeInstallation() {
        val file = File.createTempFile("model", ".tflite")
        try { file.writeText("incomplete"); assertFalse(ModelVerifier.isValid(file)) }
        finally { file.delete() }
    }
}
