import java.io.*;

public class VM {

    private static int labelCounter = 0;
    private static String currentFunction = "";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java VM <input.vm>");
            return;
        }

        String inputFile = args[0];
        String outputFile = inputFile.replace(".vm", ".asm");
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }

            line = line.split("//")[0].trim();
            String[] parts = line.split(" ");

            switch (parts[0]) {
                case "push":
                    writer.write(translatePush(parts[1], Integer.parseInt(parts[2])));
                    break;
                case "pop":
                    writer.write(translatePop(parts[1], Integer.parseInt(parts[2])));
                    break;
                case "label":
                    writer.write("(" + parts[1] + ")\n");
                    break;
                case "goto":
                    writer.write("@" + parts[1] + "\n0;JMP\n");
                    break;
                case "if-goto":
                    writer.write("@SP\nAM=M-1\nD=M\n@" + parts[1] + "\nD;JNE\n");
                    break;
                case "function":
                    writer.write(translateFunction(parts[1], Integer.parseInt(parts[2])));
                    currentFunction = parts[1];
                    break;
                case "call":
                    writer.write(translateCall(parts[1], Integer.parseInt(parts[2])));
                    break;
                case "return":
                    writer.write(translateReturn());
                    break;
                default:
                    writer.write(translateArithmetic(parts[0]));
            }
        }

        reader.close();
        writer.close();
        System.out.println("Translation completed: " + outputFile);
    }

    private static String translateArithmetic(String command) {
        switch (command) {
            case "add": return "@SP\nAM=M-1\nD=M\nA=A-1\nM=M+D\n";
            case "sub": return "@SP\nAM=M-1\nD=M\nA=A-1\nM=M-D\n";
            case "neg": return "@SP\nA=M-1\nM=-M\n";
            case "eq": return compareCommand("JEQ");
            case "gt": return compareCommand("JGT");
            case "lt": return compareCommand("JLT");
            case "and": return "@SP\nAM=M-1\nD=M\nA=A-1\nM=M&D\n";
            case "or": return "@SP\nAM=M-1\nD=M\nA=A-1\nM=M|D\n";
            case "not": return "@SP\nA=M-1\nM=!M\n";
            default: return "";
        }
    }

    private static String compareCommand(String jump) {
        String label = "LABEL" + labelCounter++;
        return "@SP\nAM=M-1\nD=M\nA=A-1\nD=M-D\n@" + label + "_TRUE\nD;" + jump +
               "\n@SP\nA=M-1\nM=0\n@" + label + "_END\n0;JMP\n(" + label + "_TRUE)\n@SP\nA=M-1\nM=-1\n(" + label + "_END)\n";
    }

    private static String translatePush(String segment, int index) {
        switch (segment) {
            case "constant": return "@" + index + "\nD=A\n@SP\nA=M\nM=D\n@SP\nM=M+1\n";
            case "local": return pushFromSegment("LCL", index);
            case "argument": return pushFromSegment("ARG", index);
            case "this": return pushFromSegment("THIS", index);
            case "that": return pushFromSegment("THAT", index);
            default: return "";
        }
    }

    private static String pushFromSegment(String base, int index) {
        return "@" + base + "\nD=M\n@" + index + "\nA=D+A\nD=M\n@SP\nA=M\nM=D\n@SP\nM=M+1\n";
    }

    private static String translatePop(String segment, int index) {
        String address = switch (segment) {
            case "local" -> "@LCL";
            case "argument" -> "@ARG";
            case "this" -> "@THIS";
            case "that" -> "@THAT";
            default -> "";
        };
        return address + "\nD=M\n@" + index + "\nD=D+A\n@R13\nM=D\n@SP\nAM=M-1\nD=M\n@R13\nA=M\nM=D\n";
    }

    private static String translateFunction(String functionName, int numLocals) {
        StringBuilder builder = new StringBuilder("(" + functionName + ")\n");
        for (int i = 0; i < numLocals; i++) {
            builder.append("@0\nD=A\n@SP\nA=M\nM=D\n@SP\nM=M+1\n");
        }
        return builder.toString();
    }

    private static String translateCall(String functionName, int numArgs) {
        String returnLabel = currentFunction + "$ret." + labelCounter++;
        return "@" + returnLabel + "\nD=A\n@SP\nA=M\nM=D\n@SP\nM=M+1\n" +  // push return address
               "@LCL\nD=M\n@SP\nA=M\nM=D\n@SP\nM=M+1\n" +              // push LCL
               "@ARG\nD=M\n@SP\nA=M\nM=D\n@SP\nM=M+1\n" +              // push ARG
               "@THIS\nD=M\n@SP\nA=M\nM=D\n@SP\nM=M+1\n" +            // push THIS
               "@THAT\nD=M\n@SP\nA=M\nM=D\n@SP\nM=M+1\n" +            // push THAT
               "@SP\nD=M\n@" + (numArgs + 5) + "\nD=D-A\n@ARG\nM=D\n" + // ARG = SP - n - 5
               "@SP\nD=M\n@LCL\nM=D\n" +                              // LCL = SP
               "@" + functionName + "\n0;JMP\n(" + returnLabel + ")\n";  // goto function, return label
    }

    private static String translateReturn() {
        return "@LCL\nD=M\n@R13\nM=D\n" +        // FRAME = LCL
               "@5\nA=D-A\nD=M\n@R14\nM=D\n" +  // RET = *(FRAME-5)
               "@SP\nAM=M-1\nD=M\n@ARG\nA=M\nM=D\n" + // *ARG = pop()
               "@ARG\nD=M+1\n@SP\nM=D\n" +     // SP = ARG + 1
               "@R13\nAM=M-1\nD=M\n@THAT\nM=D\n" +
               "@R13\nAM=M-1\nD=M\n@THIS\nM=D\n" +
               "@R13\nAM=M-1\nD=M\n@ARG\nM=D\n" +
               "@R13\nAM=M-1\nD=M\n@LCL\nM=D\n" +
               "@R14\nA=M\n0;JMP\n";           // goto RET
    }
} 