package si.sunesis.interoperability.lpc.transformations;

import com.intelligt.modbus.jlibmodbus.utils.DataUtils;

import java.util.Arrays;

public class Test {

    public static void main(String[] args) {
        System.out.println("Hello World!");

        byte[] bytes = toByteArray(50f, "int16");
        System.out.println("bytes: " + Arrays.toString(bytes));
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
