package main.visitor.codeGenerator;

import classfileanalyzer.attributes.Exceptions;
import javafx.beans.binding.When;
import main.ast.node.Program;
import main.ast.node.declaration.*;
import main.ast.node.expression.*;
import main.ast.node.statement.*;
import main.ast.type.Type;
import main.ast.type.primitiveType.NullType;
import main.ast.type.primitiveType.VoidType;
import main.ast.type.complexType.TradeType;
import main.ast.type.primitiveType.BoolType;
import main.compileError.CompileError;
import main.compileError.type.ConditionTypeNotBool;
import main.symbolTable.SymbolTable;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableItems.*;
import main.visitor.Visitor;
import main.ast.node.expression.BinaryExpression;
import main.ast.node.expression.FunctionCall;
import main.ast.node.expression.Identifier;
import main.ast.node.expression.operators.BinaryOperator;
import main.ast.node.expression.values.BoolValue;
import main.ast.node.expression.values.FloatValue;
import main.ast.node.expression.values.IntValue;
import main.ast.node.expression.values.StringValue;
import main.ast.node.expression.values.NullValue;

import main.ast.type.*;
import main.ast.type.primitiveType.BoolType;
import main.ast.type.primitiveType.FloatType;
import main.ast.type.primitiveType.IntType;
import main.ast.type.primitiveType.StringType;
import main.compileError.*;
import main.compileError.type.UnsupportedOperandType;
import main.symbolTable.SymbolTable;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableItems.FunctionItem;
import main.symbolTable.symbolTableItems.SymbolTableItem;
import main.symbolTable.symbolTableItems.VariableItem;
import main.visitor.*;
import main.ast.node.declaration.*;
import main.ast.node.statement.ForStmt;
import main.ast.node.statement.Statement;
import main.ast.node.statement.AssignStmt;
import main.ast.type.complexType.TradeType;
import main.compileError.CompileError;
import main.compileError.name.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.itemException.ItemAlreadyExistsException;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableItems.*;
import main.visitor.Visitor;
import main.visitor.typeAnalyzer.*;

import java.util.ArrayList;


import java.io.*;

public class CodeGenerator extends Visitor<String> {
//    You may use following items or add your own for handling typechecker
    TypeChecker expressionTypeChecker;
//    Graph<String> classHierarchy;
    private String outputPath;
    private FileWriter currentFile;
    private MethodDeclaration currentMethod;
    private int numberOfLabels ;

    public CodeGenerator(Graph<String> classHierarchy) {
//        this.classHierarchy = classHierarchy;

        this.expressionTypeChecker = new TypeChecker();
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
        //todo
        return null;
    }

    @Override
    public String visit(Program program) {
        //todo
        return null;
    }

    @Override
    public String visit(MethodDeclaration methodDeclaration) {
        // todo
        return null;
    }

    @Override
    public String visit(VarDeclaration varDeclaration) {
        //todo
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
        return null;
    }





    @Override
    public String visit(BlockStmt blockStmt) {
        //todo
        return null;
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
    public String visit(MethodCallStmt methodCallStmt) {
        //todo
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

    //TODO : there is a bug in NullValue definition
    @Override
    public String visit(NullValue nullValue) {
        String commands = "";
        commands += "aconst_null\n";
        return commands;
    }

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