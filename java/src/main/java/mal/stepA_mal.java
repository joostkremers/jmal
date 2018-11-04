package mal;

import java.io.Console;
import java.util.HashMap;

import mal.env.Env;
import mal.types.MalCallable;
import mal.types.MalError;
import mal.types.MalException;
import mal.types.MalFunction;
import mal.types.MalHash;
import mal.types.MalList;
import mal.types.MalSequence;
import mal.types.MalString;
import mal.types.MalSymbol;
import mal.types.MalType;
import mal.types.MalUserFunction;
import mal.types.MalVector;

public class stepA_mal {
    static Env repl_env = new Env(null);

    // `eval' is defined here, because it uses `EVAL' and closes over `repl_env'.
    static MalFunction malEval = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                if(args.size() != 1)
                    throw new MalException("Wrong number of arguments: expected 1, received " + args.size() + ".");

                return EVAL(args.get(0), repl_env);
            }
        };

    public static void main(String args[]) {
        Console console = System.console();
        String input, output;

        // Add the core functions.
        for (MalSymbol symbol : core.ns.keySet()) {
            repl_env.set(symbol, core.ns.get(symbol));
        }

        // Add `eval'.
        repl_env.set(new MalSymbol("eval"), malEval);

        // Define `not' and `load-file'.
        try {
            rep("(def! not (fn* (a) (if a false true)))");
            rep("(defmacro! or (fn* (& xs) (if (empty? xs) nil (if (= 1 (count xs)) (first xs) `(let* (or_FIXME ~(first xs)) (if or_FIXME or_FIXME (or ~@(rest xs))))))))");
            rep("(defmacro! cond (fn* (& xs) (if (> (count xs) 0) (list 'if (first xs) (if (> (count xs) 1) (nth xs 1) (throw \"odd number of forms to cond\")) (cons 'cond (rest (rest xs)))))))" );
            rep("(def! load-file (fn* (f) (eval (read-string (str \"(do \" (slurp f) \")\")))))");
        } catch(MalException ex) {
            System.out.println("Internal error. Aborting.");
            System.exit(1);
        }

        // Check if we're running a program from the command line.
        if (args.length > 0) {
            String filename = args[0];
            MalList argv = new MalList();
            for (int i = 1; i < args.length; i++) {
                argv.add(new MalString(args[i]));
            }
            repl_env.set(new MalSymbol("*ARGV*"), argv);
            try {
                rep("(load-file \"" + filename +"\")");
            } catch(MalException ex) {
                System.out.println("Error: " + ex.getMessage());
                System.exit(1);
            } finally {
                System.exit(0);
            }
        }

        // If not, set up an empty *ARGV*.
        repl_env.set(new MalSymbol("*ARGV*"), new MalList());

        while (true) {
            input = console.readLine("user> ");
            if (input == null) {      // Test for EOF
                break;
            }
            else {
                try {
                    output = rep(input);
                } catch(MalException ex) {
                    output = "*** Error *** " + ex.getMessage();
                }
            }
            System.out.println(output);
        }
    }

    public static MalType READ(String arg) throws MalException {
        return reader.read_str(arg);
    }

    public static MalType EVAL(MalType ast, Env env) throws MalException {
        while (true) {
            ast = macroexpand(ast, env);

            if (ast instanceof MalList) {
                MalList astList = (MalList)ast;
                int size = astList.size();

                // Empty list is just returned.
                if (size == 0) {
                    return ast;
                }

                // def!
                if (astList.get(0).getJValue().equals("def!")) {
                    return malDef(astList.subList(1,size), env);
                }

                // defmacro!
                if (astList.get(0).getJValue().equals("defmacro!")) {
                    return malDefMacro(astList.subList(1,size), env);
                }

                // let*
                if (astList.get(0).getJValue().equals("let*")) {
                    if (size != 3) throw new MalException("Wrong number of arguments: expected 2, received " + (size-1) + ".");
                    if (!(astList.get(1) instanceof MalSequence)) throw new MalException("Cannot let-bind: " + astList.get(1).toString());

                    ast = astList.get(2);
                    env = malLet((MalSequence)astList.get(1), env);
                    continue;
                }

                // do
                if (astList.get(0).getJValue().equals("do")) {
                    eval_ast(astList.subList(1,size-1), env);
                    ast = astList.get(size-1);
                    continue;
                }

                // if
                if (astList.get(0).getJValue().equals("if")) {
                    ast = malIf(astList.subList(1,size), env);
                    continue;
                }

                // fn*
                if (astList.get(0).getJValue().equals("fn*")) {
                    return malFn(astList.subList(1,size), env);
                }

                // macroexpand
                if (astList.get(0).getJValue().equals("macroexpand")) {
                    if (size != 2) throw new MalException("Wrong number of arguments: expected 1, received " + (size-1) + ".");
                    return macroexpand(astList.get(1), env);
                }

                // quote
                if (astList.get(0).getJValue().equals("quote")) {
                    if (size != 2) throw new MalException("Wrong number of arguments: expected 1, received " + (size-1) + ".");
                    return astList.get(1);
                }

                // quasiquote
                if (astList.get(0).getJValue().equals("quasiquote")) {
                    if (size != 2) throw new MalException("Wrong number of arguments: expected 1, received " + (size-1) + ".");
                    ast = malQuasiquote(astList.get(1));
                    continue;
                }

                // try*/catch*
                if (astList.get(0).getJValue().equals("try*")) {
                    if (size != 3) throw new MalException("Wrong number of arguments: expected 1, received " + (size-1) + ".");
                    return malTryCatch(astList.subList(1,size), env);
                }

                // If not a special form, evaluate the list as a function call.
                MalList evaledList = (MalList)eval_ast(ast, env);

                if (evaledList.get(0) instanceof MalCallable) {
                    MalCallable fn = (MalCallable)evaledList.get(0);
                    return fn.apply(evaledList.subList(1, evaledList.size()));

                } else if (evaledList.get(0) instanceof MalUserFunction) {
                    MalUserFunction fn = (MalUserFunction)evaledList.get(0);

                    ast = fn.getAst();
                    env = new Env(fn.getEnv(), fn.params.getJValue(), evaledList.subList(1,size).getJValue());
                    continue;
                } else throw new MalException("Eval error: `" + evaledList.get(0) + "' is not a function.");
            }
            // If not a list, evaluate and return.
            else return eval_ast(ast, env);
        }
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
            // if (result == null) throw new MalException("Unbound symbol: " + ast.getJValue() + ".");
            if (result == null) throw new MalException("'" + ast.getJValue() + "' not found");
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
            HashMap<MalType, MalType> astHash = ((MalHash)ast).getJValue();
            MalHash result = new MalHash();
            for(HashMap.Entry<MalType,MalType> entry : astHash.entrySet()) {
                result.put(entry.getKey(), EVAL(entry.getValue(), env));
            }
            return result;
        }

        return ast;
    }

    private static MalType malDef(MalList list, Env env) throws MalException {
        if (list.size() != 2) throw new MalException("Wrong number of arguments: expected 2, received " + list.size() + ".");
        if (!(list.get(0) instanceof MalSymbol)) throw new MalException("Cannot define non-symbol: " + list.get(0).toString());

        MalSymbol symbol = (MalSymbol)list.get(0);

        MalType evaledValue = EVAL(list.get(1), env);

        env.set(symbol, evaledValue);

        return evaledValue;
    }

    private static MalType malDefMacro(MalList list, Env env) throws MalException {
        if (list.size() != 2) throw new MalException("Wrong number of arguments: expected 2, received " + list.size() + ".");
        if (!(list.get(0) instanceof MalSymbol)) throw new MalException("Cannot define non-symbol: " + list.get(0).toString());

        MalSymbol symbol = (MalSymbol)list.get(0);

        MalType evaledValue = EVAL(list.get(1), env);

        MalUserFunction fn = evaledValue.assertType(MalUserFunction.class);
        fn.setMacro();

        env.set(symbol, fn);

        return fn;
    }

    private static Env malLet(MalSequence bindList, Env env) throws MalException {
        if ((bindList.size() % 2) != 0) throw new MalException("Odd number of elements in bind list.");

        Env letEnv = new Env(env);

        for (int i=0; i < bindList.size(); i+=2) {
            malDef(bindList.subList(i,i+2), letEnv);
        }

        return letEnv;
    }

    private static MalType malIf(MalList list, Env env) throws MalException {
        if (!(list.size() == 2 || list.size() == 3))
            throw new MalException("Wrong number of arguments: expected 2-3, received " + list.size() + ".");

        MalType test = EVAL(list.get(0), env);
        if (test.equals(types.Nil) || test.equals(types.False)) {
            if (list.size() == 2) return types.Nil;
            else return list.get(2);
        }
        else return list.get(1);
    }

    private static MalUserFunction malFn(MalList list, Env env) throws MalException {
        if (list.size() < 2) throw new MalException("fn*: argument list or body missing.");
        if (list.size() > 2) throw new MalException("fn*: body must be a single form.");

        if (!(list.get(0) instanceof MalSequence)) throw new MalException("Cannot let-bind: " + list.get(0).toString());
        MalSequence params = (MalSequence)list.get(0);
        MalType body = list.get(1);

        MalUserFunction userFn = new MalUserFunction();

        userFn.setAst(body);
        userFn.setParams(params);
        userFn.setEnv(env);

        MalFunction fn = new MalFunction() {
                @Override
                public MalType apply(MalList args) throws MalException {
                    if (params.size() != args.size())
                        // Check if we have a & in the parameter list.
                        if (!params.get(params.size()-2).getJValue().equals("&"))
                            throw new MalException("Wrong number of arguments: expected " + params.size() + ", received " + args.size());

                    Env newEnv = new Env(env, params.getJValue(), args.getJValue());
                    return EVAL(body, newEnv);
                }
            };

        userFn.setFn(fn);

        return userFn;
    }

    private static boolean is_macro_call(MalType ast, Env env) {
        if (ast instanceof MalList) {
            MalList astList = (MalList)ast;

            if ((astList.size() > 0) && (astList.get(0) instanceof MalSymbol)) {
                MalType val = env.get((MalSymbol)astList.get(0));
                if (val instanceof MalUserFunction) {
                    return ((MalUserFunction)val).isMacro();
                }
            }
        }
        return false;
    }

    private static MalType macroexpand(MalType ast, Env env) throws MalException {
        while (is_macro_call(ast, env)) {
            MalList astList = (MalList)ast;
            MalUserFunction fn = (MalUserFunction)env.get((MalSymbol)astList.get(0));

            ast = fn.apply(astList.subList(1,astList.size()));
        }

        return ast;
    }

    private static boolean is_pair(MalType arg) {
        if ((arg instanceof MalSequence) && ((MalSequence)arg).size() > 0) return true;
        else return false;
    }

    private static MalType malQuasiquote(MalType ast) throws MalException {
        MalList result = new MalList();
        MalList restList = new MalList();

        if (!is_pair(ast)) {
            result.add(new MalSymbol("quote"));
            result.add(ast);

            return result;
        }

        MalSequence astList = (MalSequence)ast;

        if (astList.get(0).getJValue().equals("unquote")) {
            return astList.get(1);
        }

        if (is_pair(astList.get(0))) {
            MalSequence firstElem = (MalSequence)astList.get(0);
            if (firstElem.get(0).getJValue().equals("splice-unquote")) {
                result.add(new MalSymbol("concat"));
                result.add(firstElem.get(1));

                restList.add(new MalSymbol("quasiquote"));
                restList.add(astList.subList(1,astList.size()));

                result.add(restList);
                return result;
            }
        }

        result.add(new MalSymbol("cons"));

        MalList firstList = new MalList();
        firstList.add(new MalSymbol("quasiquote"));
        firstList.add(astList.get(0));
        result.add(firstList);

        restList.add(new MalSymbol("quasiquote"));
        restList.add(astList.subList(1,astList.size()));
        result.add(restList);

        return result;
    }

    private static MalType malTryCatch(MalList ast, Env env) throws MalException {
        MalList catchBlock = ast.get(1).assertType(MalList.class);

        if (catchBlock.size() != 3) throw new MalException("Invalid catch* block.");

        if (!catchBlock.get(0).equals(new MalSymbol("catch*")))
            throw new MalException("try* without catch* block.");

        try {
            return EVAL(ast.get(0), env);
        } catch(MalException ex) {
            MalSymbol catchVar = catchBlock.get(1).assertType(MalSymbol.class);
            MalType catchExpr = catchBlock.get(2);

            Env catchEnv = new Env(env);
            catchEnv.set(catchVar, new MalError(ex.getErrVal()));
            return EVAL(catchExpr, catchEnv);
        }
    }
}
