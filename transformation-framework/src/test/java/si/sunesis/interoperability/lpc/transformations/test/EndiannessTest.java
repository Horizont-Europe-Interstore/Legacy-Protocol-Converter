package si.sunesis.interoperability.lpc.transformations.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import si.sunesis.interoperability.lpc.transformations.transformation.ModbusHandler;

@Slf4j
public class EndiannessTest {

    byte[] leBytes = new byte[]{0xD, 0xC, 0xB, 0xA};
    byte[] beBytes = new byte[]{0xA, 0xB, 0xC, 0xD};
    byte[] leSwapBytes = new byte[]{0xC, 0xD, 0xA, 0xB};
    byte[] beSwapBytes = new byte[]{0xB, 0xA, 0xD, 0xC};

    @Test
    public void testLeToLeSwap() {
        Assert.assertArrayEquals(leSwapBytes, ModbusHandler.leToLeSwap(leBytes));
    }

    @Test
    public void testLeToBeSwap() {
        Assert.assertArrayEquals(beSwapBytes, ModbusHandler.leToBeSwap(leBytes));
    }

    @Test
    public void testLeToBe() {
        Assert.assertArrayEquals(beBytes, ModbusHandler.leToBe(leBytes));
    }

    @Test
    public void testBeSwapToLe() {
        byte[] bytes = ModbusHandler.beSwapToBe(beSwapBytes);
        Assert.assertArrayEquals(beBytes, bytes);
        bytes = ModbusHandler.beToLe(bytes);
        Assert.assertArrayEquals(leBytes, bytes);
    }

    @Test
    public void testLeSwapToLe() {
        Assert.assertArrayEquals(leBytes, ModbusHandler.leSwapToLe(leSwapBytes));
    }
}
