package org.openmuc.j60870.ie;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class IeTestSequenceCounterTest {

    @Test(expected = IllegalArgumentException.class)
    @Parameters({"-1", "65536"})
    public void testConstrcutorRange(int i) throws Exception {
        new IeTestSequenceCounter(i);
    }

}
