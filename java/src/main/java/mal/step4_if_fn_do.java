package mal;

import java.io.Console;
import java.util.HashMap;

import mal.env.Env;
import mal.types.MalCallable;
import mal.types.MalException;
import mal.types.MalFunction;
import mal.types.MalHash;
import mal.types.MalList;
import mal.types.MalSequence;
import mal.types.MalSymbol;
import mal.types.MalType;
import mal.types.MalVector;

public class step4_if_fn_do {
    static Env repl_env = new Env(null);

    public static void main(String args[]) {
        Console console = System.console();
        String input, output;

        for (MalSymbol symbol : core.ns.keySet()) {
            repl_env.set(symbol, core.ns.get(symbol));
        }

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
            if (argList.get(0).getJValue().equals("def!")) {
                return malDef(argList.subList(1,size), env);
            }

            // let*
            if (argList.get(0).getJValue().equals("let*")) {
                return malLet(argList.subList(1,size), env);
            }

            // do
            if (argList.get(0).getJValue().equals("do")) {
                MalList result = (MalList)eval_ast(argList.subList(1,size), env);
                return result.get(size-2);
            }

            // if
            if (argList.get(0).getJValue().equals("if")) {
                return malIf(argList.subList(1,size), env);
            }

            // fn*
            if (argList.get(0).getJValue().equals("fn*")) {
                return malFn(argList.subList(1,size), env);
            }

            // If not a special form, evaluate the list as a function call.
            MalList evaledList = (MalList)eval_ast(arg, env);

            if (!(evaledList.get(0) instanceof MalCallable))
                throw new MalException("Eval error: not a function.");
            else {
                MalCallable fn = (MalCallable)evaledList.get(0);
                return fn.apply(evaledList.subList(1, evaledList.size()));
            }
        }
        // If not a list, evaluate and return.
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
            if (result == null) throw new MalException("Unbound symbol: " + ast.getJValue() + ".");
            else return result;
        }

        if (ast instanceof MalList) {
            MalList astList = (MalList)ast,
                result = new MalList();
            for(MalType elem : astList.getJValue()) {
                result.add(EVAL(elem, env));
            }
            return result;
        }

        if (ast instanceof MalVector) {
            MalVector astVector = (MalVector)ast,
                result = new MalVector();
            for(MalType elem : astVector.getJValue()) {
                result.add(EVAL(elem, env));
            }
            return result;
        }

        if (ast instanceof MalHash) {
            HashMap<MalType, MalType> astHash = (HashMap)ast.getJValue();
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

        MalType test = EVAL(list.get(0), env);
        if ((boolean)test.getJValue() == false) {
            if (list.size() == 2) return types.Nil;
            else return EVAL(list.get(2), env);
        }
        else return EVAL(list.get(1), env);
    }

    private static MalFunction malFn(MalList list, Env env) throws MalException {
        if (list.size() != 2) throw new MalException("Wrong number of arguments for `fn*': expected 2, received " + list.size() + ".");
        if (!(list.get(0) instanceof MalSequence)) throw new MalException("Cannot let-bind: " + list.get(0).toString());

        MalSequence params = (MalSequence)list.get(0);
        MalType body = list.get(1);

        MalFunction fn = new MalFunction() {
                @Override
                public MalType apply(MalList args) throws MalException {
                    if (params.size() != args.size())
                        throw new MalException("Wrong number of arguments: expected " + params.size() + ", received " + args.size());
                    Env newEnv = new Env(env, params.getJValue(), args.getJValue());
                    return EVAL(body, newEnv);
                }
            };

        return fn;
    }
}
