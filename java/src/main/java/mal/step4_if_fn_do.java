package mal;

import java.io.Console;
import java.util.HashMap;

import mal.env.Env;
import mal.types.MalCallable;
import mal.types.MalException;
import mal.types.MalFunction;
import mal.types.MalHash;
import mal.types.MalInt;
import mal.types.MalList;
import mal.types.MalSequence;
import mal.types.MalSymbol;
import mal.types.MalType;
import mal.types.MalVector;

public class step4_if_fn_do {

    // Built-in integer arithmetic functions.
    //
    // Although these functions are obvious candidates for reduce(), the fact that
    // we need to handle errors makes that too cumbersome.

    private static int checkMalInt(MalType arg) throws MalException {
        if (arg instanceof MalInt)
            return (int)arg.getValue();
        else throw new MalException("Wrong argument type: expected int, got " + arg.getType() + ".");
    }

    static MalFunction malAdd = new MalFunction() {
            @Override
            public MalInt apply(MalList args) throws MalException {
                int result = 0;

                for(MalType i : args.getValue()) {
                    result += checkMalInt(i);
                }
                return new MalInt(result);
            }
        };

    static MalFunction malSubtract = new MalFunction() {
            @Override
            public MalInt apply(MalList args) throws MalException {
                int size = args.getValue().size();

                if (size == 0) return new MalInt(0);

                int result = checkMalInt(args.get(0));
                if (size == 1) return new MalInt(-result);

                for (MalType i : args.getValue().subList(1,size)) {
                    result -= checkMalInt(i);
                }
                return new MalInt(result);
            }
        };

    static MalFunction malMultiply = new MalFunction() {
            @Override
            public MalInt apply(MalList args) throws MalException {
                int result = 1;

                for(MalType i : args.getValue()) {
                    result *= checkMalInt(i);
                }
                return new MalInt(result);
            }
        };

    static MalFunction malDivide = new MalFunction() {
            @Override
            public MalInt apply(MalList args) throws MalException {
                int size = args.getValue().size();

                if (size == 0) throw new MalException("Wrong number of arguments: required >1, received 0.");

                int result = checkMalInt(args.get(0));
                if (size == 1) return new MalInt(1/result); // These are integers, so this will always return 0.

                for (MalType i : args.getValue().subList(1,size)) {
                    result /= checkMalInt(i);
                }
                return new MalInt(result);
            }
        };

    static Env repl_env;

    static {
        repl_env = new Env(null);
        repl_env.set(new MalSymbol("+"), malAdd);
        repl_env.set(new MalSymbol("-"), malSubtract);
        repl_env.set(new MalSymbol("*"), malMultiply);
        repl_env.set(new MalSymbol("/"), malDivide);
    }

    public static void main(String args[]) {
        Console console = System.console();
        String input, output;

        while (true) {
            input = console.readLine("user> ");
            if (input == null) {      // Test for EOF
                break;
            }
            else {
                try {
                    output = rep(input);
                }
                catch(MalException ex) {
                    output = "*** Error *** " + ex.getMessage();
                }
            }
            System.out.println(output);
        }
    }

    public static MalType READ(String arg) throws MalException {
        return reader.read_str(arg);
    }

    public static MalType EVAL(MalType arg, Env env) throws MalException {
        if (arg instanceof MalList) {
            MalList argList = (MalList)arg;
            int size = argList.size();

            // Empty list is just returned.
            if (size == 0) {
                return arg;
            }

            // def!
            if (argList.get(0).getValue().equals("def!")) {
                return malDef(argList.subList(1,size), env);
            }

            // let*
            if (argList.get(0).getValue().equals("let*")) {
                return malLet(argList.subList(1,size), env);
            }

            // do
            if (argList.get(0).getValue().equals("do")) {
                MalList result = (MalList)eval_ast(argList.subList(1,size), env);
                return result.get(size-2);
            }

            // if
            if (argList.get(0).getValue().equals("if")) {
                return malIf(argList.subList(1,size), env);
            }

            MalList evaledList = (MalList)eval_ast(arg, env);

            if (!(evaledList.get(0) instanceof MalCallable))
                throw new MalException("Eval error: not a function.");
            else {
                MalCallable fn = (MalCallable)evaledList.get(0);
                return fn.apply(evaledList.subList(1, evaledList.size()));
            }
        }
        else return eval_ast(arg, env);
    }

    public static String PRINT(MalType arg) {
        return printer.pr_str(arg, true);
    }

    public static String rep(String arg) throws MalException {
        String result;

        result = PRINT(EVAL(READ(arg), repl_env));
        return result;
    }

    private static MalType eval_ast(MalType ast, Env env) throws MalException {
        if (ast instanceof MalSymbol) {
            MalType result = env.get((MalSymbol)ast);
            if (result == null) throw new MalException("Unbound symbol: " + ast.getValue() + ".");
            else return result;
        }

        if (ast instanceof MalList) {
            MalList astList = (MalList)ast,
                result = new MalList();
            for(MalType elem : astList.getValue()) {
                result.add(EVAL(elem, env));
            }
            return result;
        }

        if (ast instanceof MalVector) {
            MalVector astVector = (MalVector)ast,
                result = new MalVector();
            for(MalType elem : astVector.getValue()) {
                result.add(EVAL(elem, env));
            }
            return result;
        }

        if (ast instanceof MalHash) {
            HashMap<MalType, MalType> astHash = (HashMap)ast.getValue();
            MalHash result = new MalHash();
            for(HashMap.Entry<MalType,MalType> entry : astHash.entrySet()) {
                result.put(entry.getKey(), EVAL(entry.getValue(), env));
            }
            return result;
        }

        return ast;
    }

    private static MalType malDef(MalList list, Env env) throws MalException {
        if (list.size() != 2) throw new MalException("Wrong number of arguments for `def!': expected 2, received " + list.size() + ".");
        if (!(list.get(0) instanceof MalSymbol)) throw new MalException("Cannot define non-symbol: " + list.get(0).toString());

        MalSymbol symbol = (MalSymbol)list.get(0);

        MalType evaledValue = EVAL(list.get(1), env);

        env.set(symbol, evaledValue);

        return evaledValue;
    }

    private static MalType malLet(MalList list, Env env) throws MalException {
        if (list.size() != 2) throw new MalException("Wrong number of arguments for `let*': expected 2, received " + list.size() + ".");
        if (!(list.get(0) instanceof MalSequence)) throw new MalException("Cannot let-bind: " + list.get(0).toString());

        MalSequence bindList = (MalSequence)list.get(0);

        if ((bindList.size() % 2) != 0) throw new MalException("Odd number of elements in bind list.");

        Env letEnv = new Env(env);

        for (int i=0; i < bindList.size(); i+=2) {
            malDef(bindList.subList(i,i+2), letEnv);
        }

        return EVAL(list.get(1), letEnv);
    }

    private static MalType malIf(MalList list, Env env) throws MalException {
        if (!(list.size() == 2 || list.size() == 3))
            throw new MalException("Wrong number of arguments for `if': expected 2-3, received " + list.size() + ".");

        MalType testresult = EVAL(list.get(0), env);
        if (testresult.getValue() == false) {
            if (list.size() == 2) return MALNIL;
            else return EVAL(list.get(2), env);
        }
        else return EVAL(list.get(1), env);
    }
}
