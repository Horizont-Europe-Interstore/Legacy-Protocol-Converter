package si.sunesis.interoperability.lpc.transformations.test;

import org.junit.Test;
import si.sunesis.interoperability.lpc.transformations.transformation.ModbusHandler;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class ModbusUtilsTest {

    @Test
    public void testGetInt16At() {
        int[] registers = {0xFFC4}; // Binary: 1111111111000100 (2's complement for -60)
        assertEquals("getInt16At should handle negative 16-bit values", -60, ModbusHandler.getInt16At(0, registers));

        registers[0] = 0x7FFF; // Binary: 0111111111111111 (max positive 16-bit value)
        assertEquals("getInt16At should handle positive 16-bit values", 32767, ModbusHandler.getInt16At(0, registers));
    }

    @Test
    public void testGetInt32At() {
        int[] registers = {0xFFFF, 0xFFC4}; // Binary: 1111111111111111 1111111111000100 (2's complement for -60)
        assertEquals("getInt32At should handle negative 32-bit values", -60, ModbusHandler.getInt32At(0, registers));

        registers[0] = 0x7FFF;
        registers[1] = 0xFFFF; // Binary: 0111111111111111 1111111111111111 (max positive 32-bit value)
        assertEquals("getInt32At should handle positive 32-bit values", 2147483647, ModbusHandler.getInt32At(0, registers));
    }

    @Test
    public void testGetInt64At() {
        int[] registers = {0xFFFF, 0xFFFF, 0xFFFF, 0xFFC4}; // Binary: 1111111111111111 1111111111111111 1111111111111111 1111111111000100 (2's complement for -60)
        assertEquals("getInt64At should handle negative 64-bit values", -60L, ModbusHandler.getInt64At(0, registers));

        registers[0] = 0x7FFF;
        registers[1] = 0xFFFF;
        registers[2] = 0xFFFF;
        registers[3] = 0xFFFF; // Binary: 0111111111111111 1111111111111111 1111111111111111 1111111111111111 (max positive 64-bit value)
        assertEquals("getInt64At should handle positive 64-bit values", 9223372036854775807L, ModbusHandler.getInt64At(0, registers));
    }

    @Test
    public void testGetUInt16At() {
        int[] registers = {0xFFC4}; // Binary: 1111111111000100 (unsigned value: 65476)
        assertEquals("getUInt16At should handle unsigned 16-bit values", 65476, ModbusHandler.getUInt16At(0, registers));

        registers[0] = 0xFFFF; // Binary: 1111111111111111 (max unsigned 16-bit value: 65535)
        assertEquals("getUInt16At should handle max unsigned 16-bit values", 65535, ModbusHandler.getUInt16At(0, registers));
    }

    @Test
    public void testGetUInt32At() {
        int[] registers = {0xFFFF, 0xFFC4}; // Binary: 1111111111111111 1111111111000100 (unsigned value: 4294967236)
        assertEquals("getUInt32At should handle unsigned 32-bit values", 4294967236L, ModbusHandler.getUInt32At(0, registers));

        registers[1] = 0xFFFF; // Binary: 1111111111111111 1111111111111111 (max unsigned 32-bit value: 4294967295)
        assertEquals("getUInt32At should handle max unsigned 32-bit values", 4294967295L, ModbusHandler.getUInt32At(0, registers));
    }

    @Test
    public void testGetUInt64At() {
        int[] registers = {0xFFFF, 0xFFFF, 0xFFFF, 0xFFC4}; // Binary: 1111111111111111 1111111111111111 1111111111111111 1111111111000100 (unsigned value: 18446744073709551615)
        assertEquals("getUInt64At should handle unsigned 64-bit values", new BigInteger("18446744073709551556"), ModbusHandler.getUInt64At(0, registers));

        registers[0] = 0xFFFF;
        registers[1] = 0xFFFF;
        registers[2] = 0xFFFF;
        registers[3] = 0xFFFF; // Binary: 1111111111111111 1111111111111111 1111111111111111 1111111111111111 (max unsigned 64-bit value)
        assertEquals("getUInt64At should handle max unsigned 64-bit values", new BigInteger("18446744073709551615"), ModbusHandler.getUInt64At(0, registers));
    }
}
