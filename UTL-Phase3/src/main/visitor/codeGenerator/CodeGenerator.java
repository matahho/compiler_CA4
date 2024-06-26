package main.visitor.codeGenerator;

import main.ast.node.Program;
import main.ast.node.declaration.*;
import main.ast.node.expression.*;
import main.ast.node.expression.operators.UnaryOperator;
import main.ast.node.statement.*;
import main.ast.type.Type;
import main.ast.type.primitiveType.*;
import main.compileError.CompileError;
import main.symbolTable.SymbolTable;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableItems.*;
import main.visitor.Visitor;
import main.ast.node.expression.BinaryExpression;
import main.ast.node.expression.FunctionCall;
import main.ast.node.expression.Identifier;
import main.ast.node.expression.operators.BinaryOperator;
import main.ast.node.expression.values.BoolValue;
import main.ast.node.expression.values.IntValue;
import main.ast.node.expression.values.StringValue;

import main.symbolTable.symbolTableItems.FunctionItem;
import main.ast.node.statement.Statement;
import main.ast.node.statement.AssignStmt;
import main.visitor.typeAnalyzer.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.*;

public class CodeGenerator extends Visitor<String> {
//    You may use following items or add your own for handling typechecker
    TypeChecker expressionTypeChecker;
//    Graph<String> classHierarchy;
    private String outputPath;
    private FileWriter currentFile;
    private int numberOfLabels ;
    private int lastSlot = 0;
    private int lastLabel = 0;
    private boolean isMain = false;
    private boolean isGlobal = false;
    private boolean isLocal = false;
    private FunctionDeclaration currentFunction;
    private OnInitDeclaration currentOnInit;
    private OnStartDeclaration currentOnStart;

    private ArrayList<CompileError> typeErrors;

    private final Map<String, Integer> slot = new HashMap<>();

    public CodeGenerator(/*Graph<String> classHierarchy*/) {
//        this.classHierarchy = classHierarchy;

        this.expressionTypeChecker = new TypeChecker(typeErrors);
        this.numberOfLabels = 0 ;

//        Call your type checker here!
//        ----------------------------
        this.prepareOutputFolder();

    }

    private void prepareOutputFolder() {
        this.outputPath = "output/";
        String jasminPath = "utilities/jarFiles/jasmin.jar";
        String listClassPath = "utilities/codeGenerationUtilityClasses/List.j";
        String fptrClassPath = "utilities/codeGenerationUtilityClasses/Fptr.j";
        try{
            File directory = new File(this.outputPath);
            File[] files = directory.listFiles();
            if(files != null)
                for (File file : files)
                    file.delete();
            directory.mkdir();
        }
        catch(SecurityException e) { }
        copyFile(jasminPath, this.outputPath + "jasmin.jar");
        copyFile(listClassPath, this.outputPath + "List.j");
        copyFile(fptrClassPath, this.outputPath + "Fptr.j");
    }

    private void copyFile(String toBeCopied, String toBePasted) {
        try {
            File readingFile = new File(toBeCopied);
            File writingFile = new File(toBePasted);
            InputStream readingFileStream = new FileInputStream(readingFile);
            OutputStream writingFileStream = new FileOutputStream(writingFile);
            byte[] buffer = new byte[1024];
            int readLength;
            while ((readLength = readingFileStream.read(buffer)) > 0)
                writingFileStream.write(buffer, 0, readLength);
            readingFileStream.close();
            writingFileStream.close();
        } catch (IOException e) { }
    }

    private void createFile(String name) {
        try {
            String path = this.outputPath + name + ".j";
            File file = new File(path);
            file.createNewFile();
            FileWriter fileWriter = new FileWriter(path);
            this.currentFile = fileWriter;
        } catch (IOException e) {}
    }
    private void addStaticMainMethod() {
        addCommand(".method public static main([Ljava/lang/String;)V");
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");
        addCommand("new Main");
        addCommand("invokespecial Main/<init>()V");
        addCommand("return");
        addCommand(".end method");
    }

    private String castToNonPrimitive(Type type) {
        if (type instanceof IntType) {
            return  "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;";
        }
        else if (type instanceof BoolType) {
            return "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;";
        }

        return null;
    }

    private String castToPrimitive(Type type) {
        if (type instanceof IntType) {
            return  "invokevirtual java/lang/Integer/intValue()I";
        }
        else if (type instanceof BoolType) {
            return "invokevirtual java/lang/Boolean/booleanValue()Z";
        }
        return null;
    }
    private String checkcastType(Type t) {
        if (t instanceof IntType)
            return  "java/lang/Integer";
        else if (t instanceof BoolType)
            return "java/lang/Boolean";
//        else if (t instanceof ListType)
//            return "List";
        else if (t instanceof VoidType)
            return "V";
        return null;
    }


    private int slotOf(String identifier) {
        if(isMain){
            if (identifier.equals("")) {
                lastSlot++;
                return lastSlot;
            }
            if (!slot.containsKey(identifier)) {
                lastSlot++;
                slot.put(identifier,lastSlot);
            }
            return slot.get(identifier);
        }

        if(isGlobal || isLocal) {
            if (!slot.containsKey(identifier)) {
                lastSlot++;
                slot.put(identifier,lastSlot);
            }
            return slot.get(identifier);
        }

        if (lastSlot == 0) {
            for (VarDeclaration arg : currentFunction.getArgs()) {
                lastSlot++;
                slot.put(arg.getIdentifier().getName(), lastSlot);
            }
        }

        if (identifier.equals("")) {
            lastSlot++;
            return lastSlot;
        }
        if (!slot.containsKey(identifier)) {
            lastSlot++;
            slot.put(identifier,lastSlot);
        }

        return slot.get(identifier);
    }


    private void addCommand(String command) {
        try {
            command = String.join("\n\t\t", command.split("\n"));
            if(command.startsWith("Label_"))
                this.currentFile.write("\t" + command + "\n");
            else if(command.startsWith("."))
                this.currentFile.write(command + "\n");
            else
                this.currentFile.write("\t\t" + command + "\n");
            this.currentFile.flush();
        } catch (IOException e) {}
    }
    private String getFreshLabel() {
        return "Label_" + this.numberOfLabels++;
    }

    private String makeTypeSignature(Type t) {
        if (t instanceof IntType)
            return  "Ljava/lang/Integer;";
        else if (t instanceof BoolType)
            return "Ljava/lang/Boolean;";
        else if (t instanceof StringType)
            return "Ljava/lang/String;";
        else if (t instanceof VoidType)
            return "V";

        return null;
    }

    @Override
    public String visit(Program program) {
        prepareOutputFolder();
        createFile("Main");
        addCommand(".class public Main");
        addCommand(".super java/lang/Object");

        //Global Vars :
        isGlobal = true;
        for(VarDeclaration varDeclaration : program.getVars()){
            varDeclaration.accept(this);
        }
        isGlobal = false;

        program.getMain().accept(this);

        //Functions :
        for(FunctionDeclaration functionDeclaration :program.getFunctions()){
            functionDeclaration.accept(this);
        }

        //inits :
        for (OnInitDeclaration onInitDeclaration : program.getInits()){
            onInitDeclaration.accept(this);
        }

        //starts:
        for(OnStartDeclaration onStartDeclaration : program.getStarts()){
            onStartDeclaration.accept(this);
        }

        return null;
    }

//    @Override
//    public String visit(MethodDeclaration methodDeclaration) {
        // to do
//        return null;
//    }

    //TODO : MUST BE CHECK !!!
    @Override
    public String visit(FunctionDeclaration functionDeclaration) {
        try{
            String functionKey = FunctionItem.START_KEY + functionDeclaration.getName().getName();
            FunctionItem functionSymbolTableItem = (FunctionItem) SymbolTable.root.get(functionKey);
            SymbolTable.push(functionSymbolTableItem.getFunctionSymbolTable());
        }
        catch (ItemNotFoundException e){
            //unreachable
        }

        lastSlot = 0;
        lastLabel = 0;
        slot.clear();
        currentFunction = functionDeclaration;
        String commands = ".method public ";
        commands += functionDeclaration.getName().getName() + "(";
        for (VarDeclaration arg : functionDeclaration.getArgs()){
            commands += makeTypeSignature(arg.getType());
        }
        commands += ")";
        commands += makeTypeSignature(functionDeclaration.getReturnType());
        addCommand(commands);
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");
        isLocal = true;
        for (Statement stmt : functionDeclaration.getBody()){
            stmt.accept(this);
        }
        isLocal = false;
        addCommand(".end method");
        return null;
    }


    @Override
    public String visit(MainDeclaration mainDeclaration) {

        lastSlot = 0;
        lastLabel = 0;
        slot.clear();
//        try{
//            String functionKey = FunctionItem.START_KEY + "main";
//            FunctionItem functionSymbolTableItem = (FunctionItem)SymbolTable.root.get(functionKey);
//            SymbolTable.push(functionSymbolTableItem.getFunctionSymbolTable());
//        }catch (ItemNotFoundException e){//unreachable
       // }
        isMain = true;
        //TODO : Wrong definition of Main class and usage
        addCommand(".method public static main([Ljava/lang/String;)V");
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");
        addCommand("aload_0");
        for (Statement stmt : mainDeclaration.getBody()) {
            System.out.println(stmt);
            stmt.accept(this);
        }
        addCommand("return");
        addCommand(".end method");
        isMain = false;
        return null;
    }

    @Override
    public String visit(OnInitDeclaration onInitDeclaration){
        try{
            String onInitKey = OnInitItem.START_KEY + onInitDeclaration.getTradeName().getName();
            OnInitItem onInitSymbolTableItem = (OnInitItem) SymbolTable.root.get(onInitKey);
            SymbolTable.push(onInitSymbolTableItem.getOnInitSymbolTable());
        }
        catch (ItemNotFoundException e){
            //unreachable
        }
        lastSlot = 0;
        lastLabel = 0;
        slot.clear();
        currentOnInit = onInitDeclaration;
        String commands = ".method public ";
        commands += OnInitItem.START_KEY + onInitDeclaration.getTradeName().getName() + "(";
        commands += "Ljava/lang/Object;"; // TODO : What's the input type for Trades ?
        commands += ")V"; //no return type for onInit functions
        addCommand(commands);
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");
        isLocal = true;
        for (Statement stmt : onInitDeclaration.getBody()){
            stmt.accept(this);
        }
        isLocal = false;
        addCommand(".end method");
        return null;
    }
    @Override
    public String visit(OnStartDeclaration onStartDeclaration){
        try{
            String onStartKey = OnStartItem.START_KEY + onStartDeclaration.getTradeName().getName();
            OnStartItem onStartSymbolTableItem = (OnStartItem) SymbolTable.root.get(onStartKey);
            SymbolTable.push(onStartSymbolTableItem.getOnStartSymbolTable());
        }
        catch (ItemNotFoundException e){
            //unreachable
        }
        lastSlot = 0;
        lastLabel = 0;
        slot.clear();
        currentOnStart = onStartDeclaration;
        String commands = ".method public ";
        commands += OnStartItem.START_KEY + onStartDeclaration.getTradeName().getName() + "(";
        commands += "Ljava/lang/Object;"; // TODO : What's the input type for Trades ?
        commands += ")V"; //no return type for onStart functions
        addCommand(commands);
        addCommand(".limit stack 128");
        addCommand(".limit locals 128");
        isLocal = true;
        for (Statement stmt : onStartDeclaration.getBody()){
            stmt.accept(this);
        }
        isLocal = false;
        addCommand(".end method");
        return null;
    }


    @Override
    public String visit(VarDeclaration varDeclaration) {
        //TODO : add support for float value?
        if(!isGlobal && isLocal) {
            int slot = slotOf(varDeclaration.getIdentifier().getName());
            Type varType = varDeclaration.getType();

            if (varType instanceof IntType && varDeclaration.isArray() == false) {
                if (varDeclaration.getRValue() == null)
                    addCommand("ldc 0");
                addCommand("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            } else if (varType instanceof BoolType && varDeclaration.isArray() == false) {
                if (varDeclaration.getRValue() == null)
                    addCommand("ldc 0");
                addCommand("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
            } else if (varType instanceof FloatType && varDeclaration.isArray() == false){
                if (varDeclaration.getRValue() == null)
                    addCommand("ldc 0");
                addCommand("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
            } else if (varDeclaration.isArray() == true) {
                if (varDeclaration.getRValue() == null) {
                    addCommand("new List");
                    addCommand("dup");
                    addCommand("new java/util/ArrayList");
                    addCommand("dup");
                    addCommand("invokespecial java/util/ArrayList/<init>()V");
                }
                addCommand("invokespecial List/<init>(Ljava/util/ArrayList;)V");
            }

            addCommand("astore " + slot);
        }
        else {
            //TODO : incomplete
            Type varType = varDeclaration.getType();

            if (varType instanceof IntType && varDeclaration.isArray() == false) {
                if (varDeclaration.getRValue() == null) {
                    addCommand(".field public " + varDeclaration.getIdentifier().getName() + " I");
                    addCommand(".end field");
                } else {
                    addCommand(".field public " + varDeclaration.getIdentifier().getName() + " I = " + varDeclaration.getRValue().toString());
                    addCommand(".end field");
                }
            } else if (varType instanceof BoolType && varDeclaration.isArray() == false) {
                if (varDeclaration.getRValue() == null)
                    addCommand(".field public " + varDeclaration.getIdentifier().getName() + " I");
                else {
                    if(varDeclaration.getRValue().toString().equals("true"))
                        addCommand(".field public " + varDeclaration.getIdentifier().getName() + " I = 1");
                    else if(varDeclaration.getRValue().toString().equals("false"))
                        addCommand(".field public " + varDeclaration.getIdentifier().getName() + " I = 0");
                    addCommand(".end field");
                }
            } else if (varType instanceof FloatType) {
                if (varDeclaration.getRValue() == null) {
                    addCommand(".field public " + varDeclaration.getIdentifier().getName() + " F");
                    addCommand(".end field");
                } else {
                    addCommand(".field public " + varDeclaration.getIdentifier().getName() + " F = " + varDeclaration.getRValue().toString());
                    addCommand(".end field");
                }
            }else if (varDeclaration.isArray() == true) {
                //TODO : array definition imcomplete
                addCommand(".field public " + varDeclaration.getIdentifier().getName() + " List");
            }
        }
        return null;
    }

    @Override
    public String visit(AssignStmt assignmentStmt) {
        BinaryExpression bin_exp = new BinaryExpression(assignmentStmt.getLValue() , assignmentStmt.getRValue() , BinaryOperator.ASSIGN);
        addCommand(this.visit(bin_exp));
        addCommand("pop");
        return null;
    }

    //EXPRs :
    @Override
    public String visit(UnaryExpression unaryExpression){
        UnaryOperator operator = unaryExpression.getUnaryOperator();
        String commands = "";
        if(operator == UnaryOperator.MINUS) {
            commands += unaryExpression.getOperand().accept(this) + "\n";
            commands += "ineg";
        }
        else if(operator == UnaryOperator.NOT) {
            String falseLabel = getFreshLabel();
            String afterLabel = getFreshLabel();
            commands += unaryExpression.getOperand().accept(this) + "\n";
            commands += "ifne " + falseLabel + "\n";
            commands += "ldc 1" + "\n";
            commands += "goto " + afterLabel + "\n";
            commands += falseLabel + ":\n";
            commands += "ldc 0\n";
            commands += afterLabel + ":";
        }
        //TODO :  ++ and --
        return commands;
    }

    @Override
    public String visit(BinaryExpression binaryExpression) {
        BinaryOperator operator = binaryExpression.getBinaryOperator();
        String commands = "";

        if (operator == BinaryOperator.PLUS) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\niadd";
        }
        else if (operator == BinaryOperator.MINUS) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nisub";
        }
        else if (operator == BinaryOperator.MULT) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nimul";
        }
        else if (operator == BinaryOperator.DIV) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nidiv";
        }
        else if (operator == BinaryOperator.MOD) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nirem";
        }
        else if (operator == BinaryOperator.BIT_AND) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\niand"; // bitwise AND
        }
        else if (operator == BinaryOperator.BIT_OR) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nior"; // bitwise OR
        }
        else if (operator == BinaryOperator.BIT_XOR) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nixor"; // bitwise XOR
        }
        else if (operator == BinaryOperator.L_SHIFT) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nishl"; // left shift
        }
        else if (operator == BinaryOperator.R_SHIFT) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nishr"; // arithmetic right shift
        }

        else if((operator == BinaryOperator.GT) || (operator == BinaryOperator.LT)) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            String trueLabel = getFreshLabel();
            String afterLabel = getFreshLabel();
            if (operator == BinaryOperator.GT)
                commands += "\nif_icmpgt " + trueLabel + " ; binary gt";
            else
                commands += "\nif_icmplt " + trueLabel + " ; binary lt";
            commands += "\nldc 0"; // cond was false
            commands += "\ngoto " + afterLabel;
            commands += "\n" + trueLabel + ":";
            commands += "\nldc 1"; // cond was true
            commands += "\n" + afterLabel + ":";
        }
        else if((operator == BinaryOperator.EQ) ) {
            commands += binaryExpression.getLeft().accept(this);
            commands += "\n" + binaryExpression.getRight().accept(this);
            String trueLabel = getFreshLabel();
            String afterLabel = getFreshLabel();
            String cmpCommand = "if_a";
            Type type = binaryExpression.getLeft().accept(expressionTypeChecker);
            if (type instanceof IntType || type instanceof BoolType)
                cmpCommand = "if_i";

            commands += "\n" + cmpCommand + "cmpeq "  + trueLabel + " ; binary eq";
            commands += "\nldc 0"; // cond was false
            commands += "\ngoto " + afterLabel;
            commands += "\n" + trueLabel + ":";
            commands += "\nldc 1"; // cond was true
            commands += "\n" + afterLabel + ":";

        }
        else if(operator == BinaryOperator.AND) {
            String shortCircuitLabel = getFreshLabel();
            String trueLabel = getFreshLabel();
            String afterLabel = getFreshLabel();

            commands = "; logical AND\n";
            commands += binaryExpression.getLeft().accept(this);
            commands += "\nifeq " + shortCircuitLabel;
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nifne " + trueLabel;
            commands += "\n" + shortCircuitLabel + ":";
            commands += "\nldc 0";
            commands += "\ngoto " + afterLabel;
            commands += "\n" + trueLabel + ":";
            commands += "\nldc 1";
            commands += "\n" + afterLabel + ":";

        }
        else if(operator == BinaryOperator.OR) {
            String trueLabel = getFreshLabel();
            String afterLabel = getFreshLabel();

            commands = "; logical OR\n";
            commands += binaryExpression.getLeft().accept(this);
            commands += "\nifne " + trueLabel;
            commands += "\n" + binaryExpression.getRight().accept(this);
            commands += "\nifne " + trueLabel;
            commands += "\nldc 0";
            commands += "\ngoto " + afterLabel;
            commands += "\n" + trueLabel + ":";
            commands += "\nldc 1";
            commands += "\n" + afterLabel + ":";
        }
        else if(operator == BinaryOperator.ASSIGN) {
            commands = "";
            Type firstType = binaryExpression.getLeft().accept(expressionTypeChecker);
            Type secondType = binaryExpression.getRight().accept(expressionTypeChecker);
            String secondOperandCommands = binaryExpression.getRight().accept(this);

            //TODO

        }
        return commands;
    }

    @Override
    public String visit(IfElseStmt conditionalStmt) {
        String elseLabel = getFreshLabel();
        String exitLabel = getFreshLabel();

        //if command
        addCommand(conditionalStmt.getCondition().accept(this));

        //else command
        if (conditionalStmt.getElseBody().size() > 0)
            addCommand("ifeq " + elseLabel);
        //if body
        for (Statement stmt : conditionalStmt.getThenBody())
            stmt.accept(this);

        addCommand("goto " + exitLabel);

        addCommand(elseLabel + ":");
        if(conditionalStmt.getElseBody() != null)
            for (Statement stmt : conditionalStmt.getElseBody())
                stmt.accept(this);
        addCommand(exitLabel + ":");
        return null;

    }

    @Override
    public String visit(WhileStmt whileStmt){
        String startLabel = getFreshLabel();
        String exitLabel = getFreshLabel();
        addCommand(startLabel + ":");
        //condition of the while stmt
        addCommand(whileStmt.getCondition().accept(this));
        addCommand("ifeq " + exitLabel);
        //body of the while
        for (Statement stmt : whileStmt.getBody())
            stmt.accept(this);
        //back to top of the while cond
        addCommand("goto " + startLabel);

        // Label for the exit of the loop
        addCommand(exitLabel + ":");
        return null;

    }

    @Override
    public String visit(FunctionCall functionCall) {
        String commands = "";

        String cmd = functionCall.getFunctionName().accept(this);

        if (cmd != null)
            commands += cmd;

//
//        commands += "\n" + "new java/util/ArrayList";
//        commands += "\n" + "dup";
//        commands += "\n" + "invokespecial java/util/ArrayList/<init>()V";
//        for (Expression expression : functionCall.getArgs()) {
//            commands += "\n" + "dup";
//            commands += "\n" + expression.accept(this);
//            String castCmd = castToNonPrimitive(expression.accept(expressionTypeChecker));
//            if (castCmd != null)
//                commands += "\n" + castCmd;
//            commands += "\n" + "invokevirtual java/util/ArrayList/add(Ljava/lang/Object;)Z";
//            commands += "\n" + "pop";
//        }
//        commands += "\n" + "invokevirtual Fptr/invoke(Ljava/util/ArrayList;)Ljava/lang/Object;";
//        Type type = functionCall.accept(expressionTypeChecker);
//        commands += "\n" + "checkcast " + checkcastType(type);
//        String castCmd = castToPrimitive(type);
//        if (castCmd != null)
//            commands += "\n" + castCmd;
      return commands;
    }

    @Override
    public String visit(ExpressionStmt expressionStmt) {
        Expression expr = expressionStmt.getExpression();
        expr.accept(this);
        return null;
    }


    //TODO : grammer and nodes must be change
//    @Override
//    public String visit(PrintStmt print) {
//        addCommand("getstatic java/lang/System/out Ljava/io/PrintStream;");
//        Type argType = print.getArg().accept(expressionTypeChecker);
//        String commandsOfArg = print.getArg().accept(this);
//
//        addCommand(commandsOfArg);
//        if (argType instanceof IntType)
//            addCommand("invokevirtual java/io/PrintStream/println(I)V");
//        if (argType instanceof BoolType)
//            addCommand("invokevirtual java/io/PrintStream/println(Z)V");
//
//
//        return null;
//    }

    @Override
    public String visit(ReturnStmt returnStmt) {
        Type type = returnStmt.getReturnedExpr().accept(expressionTypeChecker);
        if(type instanceof NullType || type instanceof VoidType) {
            addCommand("return");
        }
        else {
            addCommand(returnStmt.getReturnedExpr().accept(this));
            //If the return type is Int :
            if(type instanceof IntType)
                addCommand("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            //TODO : string value must be checked ( NOT SURE )
            if(type instanceof StringType)
                addCommand("invokestatic java/lang/String/valueOf(Ljava/lang/Object;)Ljava/lang/String;");
            if(type instanceof BoolType)
                addCommand("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
        }
        return null;
    }

    @Override
    public String visit(Identifier identifier){
//        try {
            //SymbolTable.root.get(FunctionItem.START_KEY+identifier.getName());
            addCommand("new Fptr");
            addCommand("dup");
            addCommand("aload_0");
            addCommand("ldc \"" + identifier.getName() + "\"");
            addCommand("invokespecial Fptr/<init>(Ljava/lang/Object;Ljava/lang/String;)V");
            return null;
//        }
//        catch (ItemNotFoundException ex) {
//            //Unreachable
//        }
//        String commands = "";
//        int slot = slotOf(identifier.getName());
//        commands += "aload " + slot;
//        Type type = identifier.accept(expressionTypeChecker);
//        String castCmd = castToPrimitive(type);
//        if (castCmd != null)
//            commands += "\n" + castCmd;
//        return commands;
    }



//    //TODO : there is a bug in NullValue definition
//    @Override
//    public String visit(NullValue nullValue) {
//        String commands = "";
//        commands += "aconst_null\n";
//        return commands;
//    }

    @Override
    public String visit(IntValue intValue) {
        String commands = "";
        commands += "ldc " + intValue.getConstant() + "\n";
        return commands;
    }

    @Override
    public String visit(BoolValue boolValue) {
        String commands = "";
        if (boolValue.getConstant())
            commands += "ldc 1\n";
        else
            commands += "ldc 0\n";
        return commands;
    }

    @Override
    public String visit(StringValue stringValue) {
        String commands = "";
        String constant = stringValue.getConstant();

        // Replace each newline character in the constant with \n
        constant = constant.replace("\n", "\\n");

        // quatitioan added to first and last of the value
        commands += "ldc \"" + constant + "\"\n";
        return commands;
    }


}