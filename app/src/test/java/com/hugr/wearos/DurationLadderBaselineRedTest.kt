package com.hugr.wearos

import org.junit.Test
import org.junit.Assert.*

/** Same assertion exercised against the preserved Watch82 baseline first. */
class DurationLadderBaselineRedTest {
    @Test fun thirtyMinuteClosedDurationReceiptMustBeReadable() {
        val text="HUGR_RUN_RECEIPT_V2\nrun\t09146edf-d758-4e73-9f6d-d66a85551a36\t11111111-1111-4111-8111-111111111111\t1\tSTARTED\ntime\t1800000\t1000000\t1000\t0\t0\t1\nscope\t31\t31\tNONE\nend\n"
        assertNotNull(FreshRunReceipt.parse(text.toByteArray(Charsets.US_ASCII)))
    }
}
