package si.sunesis.interoperability.lpc.transformations;

import com.intelligt.modbus.jlibmodbus.utils.DataUtils;
import si.sunesis.interoperability.lpc.transformations.transformation.ModbusHandler;

import java.util.Arrays;

public class Test {

    public static void main(String[] args) {
        System.out.println("Hello World!");

        byte[] bytes = toByteArray(50f, "int16");
        System.out.println("bytes: " + Arrays.toString(bytes));

        byte[] bytes2 = {-1, -1, -4, 28};
        byte[] leToLeSwap = ModbusHandler.leToLeSwap(bytes2);
        byte[] beToLe = ModbusHandler.beToLe(bytes2);
        byte[] leToBeSwap = ModbusHandler.leToBeSwap(bytes2);

        System.out.println("bytes2: " + Arrays.toString(bytes2));
        System.out.println("leToLeSwap: " + Arrays.toString(leToLeSwap));
        System.out.println("beToLe: " + Arrays.toString(beToLe));
        System.out.println("leToBeSwap: " + Arrays.toString(leToBeSwap));

        int[] regs = DataUtils.BeToRegArray(bytes2);
        int[] leToLeSwapRegs = DataUtils.BeToRegArray(leToLeSwap);
        int[] beToLeRegs = DataUtils.BeToRegArray(beToLe);
        int[] leToBeSwapRegs = DataUtils.BeToRegArray(leToBeSwap);

        System.out.println("regs: " + Arrays.toString(regs));
        System.out.println("leToLeSwapRegs: " + Arrays.toString(leToLeSwapRegs));
        System.out.println("beToLeRegs: " + Arrays.toString(beToLeRegs));
        System.out.println("leToBeSwapRegs: " + Arrays.toString(leToBeSwapRegs));

        System.out.println("regs: " + ModbusHandler.getInt32At(0, regs));
        System.out.println("leToLeSwapRegs: " + ModbusHandler.getInt32At(0, leToLeSwapRegs));
        System.out.println("beToLeRegs: " + ModbusHandler.getInt32At(0, beToLeRegs));
        System.out.println("leToBeSwapRegs: " + ModbusHandler.getInt32At(0, leToBeSwapRegs));
    }


    private static byte[] toByteArray(Float value, String type) {
        byte[] bytes = DataUtils.toByteArray(value);
        if (type.contains("int")) {
            if (type.contains("16")) {
                bytes = DataUtils.toByteArray(value.shortValue());
            } else if (type.contains("8")) {
                bytes = DataUtils.toByteArray(value.byteValue());
            } else if (type.contains("64")) {
                bytes = DataUtils.toByteArray(value.longValue());
            } else {
                bytes = DataUtils.toByteArray(value.intValue());
            }
        } else if (type.contains("long")) {
            bytes = DataUtils.toByteArray(value.longValue());
        } else if (type.contains("short")) {
            bytes = DataUtils.toByteArray(value.shortValue());
        } else if (type.contains("byte")) {
            bytes = DataUtils.toByteArray(value.byteValue());
        }

        return bytes;
    }
}
