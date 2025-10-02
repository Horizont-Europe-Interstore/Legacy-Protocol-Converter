import argparse
import json
import logging
import os
import traceback

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from pymodbus.client import ModbusTcpClient
from pymodbus.exceptions import ModbusIOException

# Logging Configuration
FORMAT = '%(asctime)s - %(levelname)s - %(message)s'
logging.basicConfig(format=FORMAT, level=logging.DEBUG, handlers=[logging.StreamHandler()])

# FastAPI app initialization
app = FastAPI(title="Modbus TCP API", description="FastAPI server for Modbus communication")


# Define request model for FastAPI
class ModbusRequest(BaseModel):
    host: str
    port: int
    unit_id: int
    start_register: int
    function_code: int
    values: list[int] | None = None
    count: int | None = 1


def modbus_request(host, port, unit_id, start_register, function_code, values, count):
    response_data = {"status": "error", "message": "Unknown error", "data": None}

    try:
        modbus_client = ModbusTcpClient(host, port=port)

        if not modbus_client.connect():
            response_data["message"] = "Failed to connect to Modbus server"
            #print(json.dumps(response_data))  # Output JSON
            return response_data

        response_modbus = None

        match function_code:
            case 1:  # Read Coils
                print("Reading Coils")
                response_modbus = modbus_client.read_coils(address=start_register, count=count, device_id=unit_id)
            case 2:  # Read Discrete Inputs
                print("Reading Discrete Inputs")
                response_modbus = modbus_client.read_discrete_inputs(address=start_register, count=count, device_id=unit_id)
            case 3:  # Read Holding Registers
                print("Reading Holding Registers")
                response_modbus = modbus_client.read_holding_registers(address=start_register, count=count,
                                                                       device_id=unit_id)
            case 4:  # Read Input Registers
                print("Reading Input Registers")
                response_modbus = modbus_client.read_input_registers(address=start_register, count=count, device_id=unit_id)
            case 5:  # Write Single Coil
                print("Reading Output Registers")
                if values is None or len(values) != 1:
                    response_data["message"] = "Error: --values must contain exactly one value (0 or 1) for function code 5"
                    return response_data
                response_modbus = modbus_client.write_coil(start_register, values[0] > 0, device_id=unit_id)
            case 6:  # Write Single Register
                print("Writing Single Register")
                if values is None or len(values) != 1:
                    response_data["message"] = "Error: --values must contain exactly one value for function code 6"
                    return response_data
                response_modbus = modbus_client.write_register(start_register, values[0], device_id=unit_id)
            case 16:  # Write Multiple Registers
                print("Writing Multiple Registers")
                if values is None:
                    response_data["message"] = "Error: --values must be specified for function code 11"
                    return response_data
                response_modbus = modbus_client.write_registers(start_register, values, device_id=unit_id)
            case 15:  # Write Multiple Coils
                print("Writing Multiple Coils")
                if values is None:
                    response_data["message"] = "Error: --values must be specified for function code 15"
                    return response_data
                response_modbus = modbus_client.write_coils(start_register, [v > 0 for v in values], device_id=unit_id)
            case 23:  # Read Write Multiple Registers
                print("Read Write Multiple Registers")
                if values is None:
                    response_data["message"] = "Error: --values must be specified for function code 16"
                    return response_data

                print(values)
                response_modbus = modbus_client.readwrite_registers(address=start_register,
                                                                    values=values,
                                                                    read_count=count,
                                                                    device_id=unit_id)
            case _:
                print(f"Unsupported function code: {function_code}")
                response_data["message"] = f"Unsupported function code: {function_code}"
                return response_data

        # Handle response
        if response_modbus is None or isinstance(response_modbus, ModbusIOException):
            response_data["message"] = f"Failed to execute function code {function_code} at address {start_register}"
        else:
            response_data["status"] = "success"
            response_data[
                "message"] = f"Successfully executed function code {function_code} at address {start_register}"

            if function_code in [1, 2]:  # Read Coils & Discrete Inputs return bits
                print(f"Bits: {response_modbus.bits}")
                response_data["data"] = response_modbus.bits
            elif function_code in [3, 4, 23]:  # Read Registers return registers
                print(f"Registers: {response_modbus.registers}")
                response_data["data"] = response_modbus.registers
            else:
                response_data["data"] = "Write operation successful"

        # Close connection
        modbus_client.close()

        return response_data
    except Exception as e:
        # Print stack trace
        print(traceback.format_exc())

        return {"status": "error", "message": f"Error during Modbus communication: {e}", "data": None}


@app.post("/modbus")
def modbus_api(request: ModbusRequest):
    response_modbus = modbus_request(
        host=request.host,
        port=request.port,
        unit_id=request.unit_id,
        start_register=request.start_register,
        function_code=request.function_code,
        values=request.values,
        count=request.count
    )

    if response_modbus["status"] == "error":
        raise HTTPException(status_code=400, detail=response_modbus["message"])

    return response_modbus


if __name__ == '__main__':
    import uvicorn

    parser = argparse.ArgumentParser(description="Modbus TCP Client with FastAPI Support")

    parser.add_argument("--host", type=str, default=os.getenv("MODBUS_HOST", "host.docker.internal"),
                        help="Modbus server IP address")
    parser.add_argument("--port", type=int, default=int(os.getenv("MODBUS_PORT", 502)), help="Modbus TCP port")
    parser.add_argument("--unit_id", type=int, default=1, help="Unit ID of the Modbus device")
    parser.add_argument("--start_register", type=int, help="Start register or coil address")
    parser.add_argument("--function_code", type=int, choices=[1, 2, 3, 4, 5, 6, 11, 15, 16],
                        help="Modbus function code")
    parser.add_argument("--values", type=int, nargs="+", help="Values to write (for write functions)")
    parser.add_argument("--count", type=int, default=1, help="Number of registers/coils to read")
    parser.add_argument("--api", action="store_true", help="Start FastAPI server")
    parser.add_argument("--api_port", type=int, default=8000, help="FastAPI server port")

    args = parser.parse_args()

    if args.api:
        uvicorn.run("pymodbus_script:app", host="0.0.0.0", port=args.api_port)
    else:
        if args.start_register is None or args.function_code is None:
            print("Error: --start_register and --function_code are required for CLI usage.")
            exit(1)

        response = modbus_request(
            host=args.host,
            port=args.port,
            unit_id=args.unit_id,
            start_register=args.start_register,
            function_code=args.function_code,
            values=args.values,
            count=args.count
        )
        print(json.dumps(response))
