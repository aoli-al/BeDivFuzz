package de.hub.se.jqf.bedivfuzz.examples.python;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import de.hub.se.jqf.bedivfuzz.junit.quickcheck.SplitGenerator;
import de.hub.se.jqf.bedivfuzz.junit.quickcheck.SplitRandom;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assume.assumeTrue;

public class SplitPythonGenerator extends SplitGenerator<String> {
    public SplitPythonGenerator() {
        super(String.class); // Register type of generated object

        // Read parameters from system properties
        maxIdentifiers = Integer.getInteger(identifierProp, 3);
        maxItems = Integer.getInteger(itemsProp, 3);
        maxDepth = Integer.getInteger(depthProp, 3);
        maxBound = 4;
        assert(maxIdentifiers > 0);
        assert(maxItems > 0);
        assert(maxDepth > 0);

        // Create set of fixed identifiers
        generateIdentifiers(maxBound);
    }

    private final String identifierProp = "maxIdentifiers";
    private final String itemsProp = "maxItems";
    private final String depthProp = "maxDepth";
    private GenerationStatus status; // saved state object when generating
    private static List<String> identifiers; // Stores generated IDs, to promote re-use
    private static List<String> funcIdentifiers; // Stores generated IDs, to promote re-use
    private static List<String> allTypes; // Keeps track of all types
    private static List<String> classTypes; // Keeps track of all types
    private static int maxIdentifiers;
    private static int maxItems;
    private static int maxDepth;
    private static int maxBound;
    private int statementDepth; // Keeps track of how deep the AST is at any point
    private int declarationDepth; // Keeps track of how deep the AST is at any point
    private int expressionDepth; // Keeps track of how nested an expression is at any point
    private int indentLevel; // Keeps track of indentation level

    private static final String[] BINARY_TOKENS = {
            "+", "-", "*", "//", "%", "and", "or",
            "<", "<=", ">", ">=", "==", "!=", "is"
    };

    private static final String[] BINARY_BOOL_TOKENS = {
            "and", "or"
    };

    private static final String[] BASE_TYPES = {
            "int", "str", "bool", "object"
    };

    private static final String[] INT_LITERALS = {
            "0", "1"
    };

    private static final String[] STRING_LITERALS = {
            "\"a\"", "\"\""
    };

    private static final String[] BOOL_LITERALS = {
            "True", "False"
    };

    private static final String INDENT_TOKEN = "    "; // 4 spaces

    @Override
    public String generate(SplitRandom random, GenerationStatus status) {
        this.status = status; // we save this so that we can pass it on to other generators
        this.declarationDepth = 0;
        this.statementDepth = 0;
        this.expressionDepth = 0;
        this.indentLevel = 0;
        this.classTypes = new ArrayList<>();
        this.classTypes.add("object");
        this.allTypes = Arrays.asList(BASE_TYPES);
        this.allTypes = new ArrayList<>(this.allTypes);
        for (String identifier : identifiers) {
            this.allTypes.add(identifier);
            this.classTypes.add(identifier);
        }
        return generateProgram(random);
    }

    /** Utility method for generating a random list of items (e.g. statements, arguments, attributes) */
    private static List<String> generateItems(Function<SplitRandom, String> genMethod, SplitRandom random, int minimum) {
        int len = nextIntBound(random, minimum, maxBound, maxItems);
        List<String> items = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            items.add(genMethod.apply(random));
        }
        return items;
    }

    /** Utility method for generating a random list of items from a list of functions to choose from */
    private static List<String> generateItemsMultipleMethods(List<Function<SplitRandom, String>> genMethods,
                                                             SplitRandom random, int minimum) {
        int len = nextIntBound(random, minimum, maxBound, maxItems);
        List<String> items = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            items.add(random.chooseStructure(genMethods).apply(random));
        }
        return items;
    }

    private static int nextIntBound(SplitRandom random, int minimum, int maximum, int maxParam) {
        int randInt = random.nextStructureInt(minimum, maximum);
        assumeTrue(randInt <= maxParam);
        return randInt;
    }

    /** Generates a random ChocoPy program of classes, declarations, and statements */
    private String generateProgram(SplitRandom random) {
        String declarations = String.join("", generateItemsMultipleMethods(Arrays.asList(
                this::generateClassDef,
                this::generateFuncDef,
                this::generateVarDef
        ), random, 0));
        String statements = generateBlock(random, 0);
        return declarations + statements;
    }

    /** Generates a random ChocoPy declaration */
    private String generateDeclaration(SplitRandom random) {
        String result = StringUtils.repeat(INDENT_TOKEN, indentLevel);
        int randDepth = nextIntBound(random, 0, maxBound, maxDepth);
        if (declarationDepth >= randDepth) {
            // Choose a random private method from this class, and then call it with `random`
            result += generateVarDef(random);
        } else {
            // If depth is low and we won the flip, then generate compound declarations
            // (that is, declarations that contain other declarations)
            result += random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateFuncDef
            )).apply(random);
        }
        return result;
    }

    /** Generates a random ChocoPy function declaration */
    private String generateFuncDeclaration(SplitRandom random) {
        String result = StringUtils.repeat(INDENT_TOKEN, indentLevel);
        int randDepth = nextIntBound(random, 0, maxBound, maxDepth);
        if (declarationDepth >= randDepth) {
            // Choose a random private method from this class, and then call it with `random`
            result += random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateVarDef,
                    this::generateNonlocalDecl,
                    this::generateGlobalDecl
            )).apply(random);
        } else {
            // If depth is low and we won the flip, then generate compound declarations
            // (that is, declarations that contain other declarations)
            result += random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateFuncDef
            )).apply(random);
        }
        return result + "\n";
    }

    /** Generates a random ChocoPy statement */
    private String generateStatement(SplitRandom random) {
        String result = StringUtils.repeat(INDENT_TOKEN, indentLevel);
        // If depth is too high, then generate only simple statements to prevent infinite recursion
        // If not, generate simple statements after the flip of a coin
        int randDepth = nextIntBound(random, 0, maxBound, maxDepth);
        if (statementDepth >= randDepth) {
            // Choose a random private method from this class, and then call it with `random`
            result += random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateAssignStmt,
                    this::generatePassStmt,
                    this::generateReturnStmt,
                    this::generateExpressionStmt
            )).apply(random);
        } else {
            // If depth is low and we won the flip, then generate compound statements
            // (that is, statements that contain other statements)
            result += random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateIfStmt,
                    this::generateForStmt,
                    this::generateWhileStmt
            )).apply(random);
        }
        return result + "\n";
    }

    /** Generates a random ChocoPy expression using recursive calls */
    private String generateExpression(SplitRandom random) {
        String result;
        // Choose terminal if nesting depth is too high or based on a random flip of a coin
        int randDepth = nextIntBound(random, 0, maxBound, maxDepth);
        if (expressionDepth >= randDepth) {
            result = generateCExpression(random);
        } else {
            expressionDepth++;
            // Otherwise, choose a non-terminal generating function
            result = random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateUnaryBoolExpr,
                    this::generateBinaryBoolExpr,
                    this::generateIfExpr
            )).apply(random);
            expressionDepth--;
        }
        return result;
    }

    private String generateCExpression(SplitRandom random) {
        expressionDepth++;
        String result;
        // Choose terminal if nesting depth is too high or based on a random flip of a coin
        int randDepth = nextIntBound(random, 0, maxBound, maxDepth);
        if (expressionDepth >= randDepth) {
            result = random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateLiteral,
                    this::generateIdentifier
            )).apply(random);
        } else {
            // Otherwise, choose a non-terminal generating function
            result = random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateListExpr,
                    this::generateParenExpr,
                    this::generateMemberExpr,
                    this::generateMethodCallExpr,
                    this::generateIndexExpr,
                    this::generateCallExpr,
                    this::generateBinaryExpr,
                    this::generateUnaryExpr
            )).apply(random);
        }
        expressionDepth--;
        return result;
    }

    private String generateAssignStmt(SplitRandom random) {
        String result = "";
        int len = nextIntBound(random, 1, maxBound, maxDepth);
        List<String> items = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            String target = random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                    this::generateIdentifier,
                    this::generateIndexExpr,
                    this::generateMemberExpr
            )).apply(random);
            result += target + " = ";
        }
        return result + generateExpression(random);
    }

    private String generateBinaryBoolExpr(SplitRandom random) {
        String token = random.chooseValue(BINARY_BOOL_TOKENS);
        String lhs = generateCExpression(random);
        String rhs = generateCExpression(random);

        return lhs + " " + token + " " + rhs;
    }

    /** Generates a random binary expression (e.g. A op B) */
    private String generateBinaryExpr(SplitRandom random) {
        String token = random.chooseValue(BINARY_TOKENS);
        String lhs = generateCExpression(random);
        String rhs = generateCExpression(random);

        return lhs + " " + token + " " + rhs;
    }

    /** Generates a block of statements */
    private String generateBlock(SplitRandom random, int minimum) {
        return String.join("", generateItems(this::generateStatement, random, minimum));
    }

    private String generateCallExpr(SplitRandom random) {
        String func = generateFuncIdentifier(random);
        String args = String.join(",", generateItems(this::generateExpression, random, 0));

        String call = func + "(" + args + ")";
        return call;
    }

    private String generateClassDef(SplitRandom random) {
        String result = "";
        String className = generateIdentifier(random);
        // Superclass could be one of the identifiers or object. Index should be from 0 to maxIdentifiers inclusive.
        int superClassIndex = nextIntBound(random, 0, classTypes.size(), maxIdentifiers);
        String superClassName = classTypes.get(superClassIndex);
        result += "class " + className + "(" + superClassName + "):\n";
        indentLevel++;
        result += generateDeclarationBlock(random, 1);
        indentLevel--;
        return result + "\n";
    }

    /** Generates a block of VarDefs and FuncDefs*/
    private String generateDeclarationBlock(SplitRandom random, int minimum) {
        return String.join("", generateItems(this::generateDeclaration, random, minimum));
    }

    private String generateExpressionStmt(SplitRandom random) {
        return generateExpression(random);
    }

    private String generateForStmt(SplitRandom random) {
        statementDepth++;
        String s = "for " + generateIdentifier(random) + " in " + generateExpression(random) + ":\n";
        indentLevel++;
        s += generateBlock(random, 1);
        indentLevel--;
        statementDepth--;
        return s;
    }

    private String generateFuncDef(SplitRandom random) {
        declarationDepth++;
        String result = "def " + generateFuncIdentifier(random) + "("
                + String.join(", ", generateItems(this::generateTypedVar, random, 0)) + ")";
        result += ":\n";
        indentLevel++;
        result += String.join("", generateItems(this::generateFuncDeclaration, random, 0));
        result += generateBlock(random, 1);
        indentLevel--;
        declarationDepth--;
        return result + "\n";
    }

    private String generateGlobalDecl(SplitRandom random) {
        return "global " + generateIdentifier(random) + "\n";
    }

    private String generateIdentifier(SplitRandom random) {
        int index = nextIntBound(random, 0, maxBound, maxIdentifiers - 1);
        return identifiers.get(index);
    }

    private String generateFuncIdentifier(SplitRandom random) {
        int index = nextIntBound(random, 0, funcIdentifiers.size(),
                maxIdentifiers + BASE_TYPES.length);
        return funcIdentifiers.get(index);
    }

    /** Creates initial set of identifiers depending on parameter */
    private void generateIdentifiers(int numIdentifiers) {
        this.identifiers = new ArrayList<>();
        this.funcIdentifiers = new ArrayList<>(Arrays.asList(BASE_TYPES));
        funcIdentifiers.add("len");
        String ident;
        for (int i = 0; i < numIdentifiers; i++) {
            ident = "a" + i;
            identifiers.add(ident);
            funcIdentifiers.add(ident);
        }
    }

    private String generateIfExpr(SplitRandom random) {
        return generateExpression(random) + " if " + generateExpression(random) + " else " + generateExpression(random);
    }

    private String generateIfStmt(SplitRandom random) {
        statementDepth++;
        String result = "if " + generateExpression(random) + ":\n";
        indentLevel++;
        result += generateBlock(random, 1);
        indentLevel--;
        if (random.nextStructureBoolean()) {
            result += StringUtils.repeat(INDENT_TOKEN, indentLevel);
            result += "elif " + generateExpression(random) + ":\n";
            indentLevel++;
            result += generateBlock(random, 1);
            indentLevel--;
        }
        if (random.nextStructureBoolean()) {
            result += StringUtils.repeat(INDENT_TOKEN, indentLevel);
            result += "else:\n";
            indentLevel++;
            result += generateBlock(random, 1);
            indentLevel--;
        }
        statementDepth--;
        return result;
    }

    private String generateIndexExpr(SplitRandom random) {
        return "(" + generateCExpression(random) + ")[" + generateExpression(random) + "]";
    }

    private String generateListExpr(SplitRandom random) {
        return "[" + String.join(", ", generateItems(this::generateExpression, random, 0)) + "]";
    }

    // Generate fixed primitive literals
    private String generateLiteral(SplitRandom random) {

        return random.chooseStructure(Arrays.<Function<SplitRandom, String>>asList(
                this::generateIntLiteral,
                this::generateStringLiteral,
                this::generateBoolLiteral,
                this::generateNoneLiteral)).apply(random);
    }

    private String generateIntLiteral(SplitRandom random) {
        return random.chooseValue(INT_LITERALS);
    }

    private String generateStringLiteral(SplitRandom random) {
        return random.chooseValue(STRING_LITERALS);
    }

    private String generateBoolLiteral(SplitRandom random) {
        return random.chooseValue(BOOL_LITERALS);
    }

    private String generateNoneLiteral(SplitRandom random) {
        return "None";
    }

    private String generateMemberExpr(SplitRandom random) {
        return "(" + generateCExpression(random) + ")." + generateIdentifier(random);
    }

    private String generateMethodCallExpr(SplitRandom random) {
        return generateCExpression(random) + "." + generateCallExpr(random);
    }

    private String generateNonlocalDecl(SplitRandom random) {
        return "nonlocal " + generateIdentifier(random) + "\n";
    }

    private String generateParenExpr(SplitRandom random) {
        return "(" + generateExpression(random) + ")";
    }

    private String generatePassStmt(SplitRandom random) {
        return "pass";
    }

    private String generateReturnStmt(SplitRandom random) {
        return random.nextStructureBoolean() ? "return" : "return " + generateExpression(random);
    }

    /** Randomly choose from types and random list depth using maxDepth parameter */
    private String generateType(SplitRandom random) {
        int typeIndex = nextIntBound(random, 0, allTypes.size(), BASE_TYPES.length + maxIdentifiers - 1);
        String type = allTypes.get(typeIndex);
        int listDepth = nextIntBound(random, 0, maxBound, maxDepth);
        for (int i = 0; i < listDepth; i++) {
            type = "[" + type + "]";
        }
        return type;
    }

    private String generateTypedVar(SplitRandom random) {
        return generateIdentifier(random);
    }

    private String generateUnaryBoolExpr(SplitRandom random) {
        return "not " + generateExpression(random);
    }

    private String generateUnaryExpr(SplitRandom random) {
        return "-" + generateCExpression(random);
    }

    private String generateVarDef(SplitRandom random) {
        return generateTypedVar(random) + " = " + generateLiteral(random) + "\n";
    }

    private String generateWhileStmt(SplitRandom random) {
        statementDepth++;
        indentLevel++;
        String result = "while " + generateExpression(random) + ":\n" + generateBlock(random, 1);
        indentLevel--;
        statementDepth--;
        return result;
    }

}
