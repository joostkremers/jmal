package mal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;

import mal.types.MalAtom;
import mal.types.MalException;
import mal.types.MalFunction;
import mal.types.MalInt;
import mal.types.MalList;
import mal.types.MalSequence;
import mal.types.MalString;
import mal.types.MalSymbol;
import mal.types.MalType;

public class core {
    // Built-in integer arithmetic functions.
    //
    // Although these functions are obvious candidates for reduce(), the fact that
    // we need to handle errors makes that too cumbersome.

    /**
     * Check the number of arguments passed to a function.
     * <p>
     * This function checks that the argument list has exactly the specified
     * number of arguments.
     *
     * @param list The list of arguments to be checked.
     * @param n The number of arguments required.
     */
    private static void assertNArgs(MalList list, int n) throws MalException {
        int size = list.getJValue().size();

        if (size != n) {
            String errormsg = String.format("Wrong number of arguments: required %d, received %d.", n, size);
            throw new MalException(errormsg);
        }
    }

    /**
     * Check the number of arguments passed to a function.
     * <p>
     * This function checks that the number of arguments in the list falls
     * between the bounds given.
     *
     * @param list The list of arguments to be checked.
     * @param min The minimum number of arguments required.
     * @param max The maximum number of arguments allowed.
     */
    private static void assertNArgs(MalList list, int min, int max) throws MalException {
        int size = list.getJValue().size();

        if ((size < min) || (size > max)) {
            String errormsg = String.format("Wrong number of arguments: required %d-%d, received %d.", min, max, size);
            throw new MalException(errormsg);
        }
    }

    /**
     * Check the number of arguments passed to a function.
     * <p>
     * This function checks that the argument list has at least the minimum
     * number of arguments required. There is no upper bound.
     *
     * @param list The list of arguments to be checked.
     * @param min The minimum number of arguments required.
     */
    private static void assertMinArgs(MalList list, int min) throws MalException {
        int size = list.getJValue().size();

        if (size < min) {
            String errormsg = String.format("Wrong number of arguments: required at least %d, received %d.", min, size);
            throw new MalException(errormsg);
        }
    }

    static MalFunction malAdd = new MalFunction() {
            @Override
            public MalInt apply(MalList args) throws MalException {
                int result = 0;

                for(MalType i : args.getJValue()) {
                    result += i.assertType(MalInt.class).getJValue();
                }
                return new MalInt(result);
            }
        };

    static MalFunction malSubtract = new MalFunction() {
            @Override
            public MalInt apply(MalList args) throws MalException {
                int size = args.getJValue().size();

                if (size == 0) return new MalInt(0);

                int result = args.get(0).assertType(MalInt.class).getJValue();
                if (size == 1) return new MalInt(-result);

                for (MalType i : args.getJValue().subList(1,size)) {
                    result -= i.assertType(MalInt.class).getJValue();
                }
                return new MalInt(result);
            }
        };

    static MalFunction malMultiply = new MalFunction() {
            @Override
            public MalInt apply(MalList args) throws MalException {
                int result = 1;

                for(MalType i : args.getJValue()) {
                    result *= i.assertType(MalInt.class).getJValue();
                }
                return new MalInt(result);
            }
        };

    static MalFunction malDivide = new MalFunction() {
            @Override
            public MalInt apply(MalList args) throws MalException {
                assertNArgs(args, 1);

                int result = args.get(0).assertType(MalInt.class).getJValue();
                int size = args.size();
                if (size == 1) return new MalInt(1/result); // These are integers, so this will always return 0.

                for (MalType i : args.getJValue().subList(1,size)) {
                    result /= i.assertType(MalInt.class).getJValue();
                }
                return new MalInt(result);
            }
        };

    static MalFunction malList = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                return args;
            }
        };

    static MalFunction malListP = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                assertNArgs(args, 1);

                if (args.get(0) instanceof MalList) return types.True;
                else return types.False;
            }
        };

    static MalFunction malEmptyP = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                assertNArgs(args, 1);
                int size = args.get(0).assertType(MalSequence.class).size();

                if (size == 0) return types.True;
                else return types.False;
            }
        };

    static MalFunction malCount = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                assertNArgs(args, 1);

                if (args.get(0) == types.Nil) return new MalInt(0);

                int size = args.get(0).assertType(MalSequence.class).size();

                return new MalInt(size);
            }
        };

    static MalFunction malEqual = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                assertNArgs(args, 2);

                if (args.get(0).equals(args.get(1))) return types.True;
                else return types.False;
            }
        };

    static MalFunction malLessThan = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                int size = args.size();
                MalInt arg1 = args.get(0).assertType(MalInt.class);

                for (int i = 1; i < size; i++) {
                    MalInt arg2 = args.get(i).assertType(MalInt.class);

                    if (!arg1.isLessThan(arg2)) return types.False;
                    arg1 = arg2;
                }
                return types.True;
            }
        };

    static MalFunction malLessThanOrEqual = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                int size = args.size();
                MalInt arg1 = args.get(0).assertType(MalInt.class);

                for (int i = 1; i < size; i++) {
                    MalInt arg2 = args.get(i).assertType(MalInt.class);

                    if (!arg1.isLessThanOrEqual(arg2)) return types.False;
                    arg1 = arg2;
                }
                return types.True;
            }
        };

    static MalFunction malGreaterThan = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                int size = args.size();
                MalInt arg1 = args.get(0).assertType(MalInt.class);

                for (int i = 1; i < size; i++) {
                    MalInt arg2 = args.get(i).assertType(MalInt.class);

                    if (!arg1.isGreaterThan(arg2)) return types.False;
                    arg1 = arg2;
                }
                return types.True;
            }
        };

    static MalFunction malGreaterThanOrEqual = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                int size = args.size();
                MalInt arg1 = args.get(0).assertType(MalInt.class);

                for (int i = 1; i < size; i++) {
                    MalInt arg2 = args.get(i).assertType(MalInt.class);

                    if (!arg1.isGreaterThanOrEqual(arg2)) return types.False;
                    arg1 = arg2;
                }
                return types.True;
            }
        };

    // Strings functions.
    static MalFunction malPrStr = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                StringJoiner result = new StringJoiner(" ");

                for (MalType item : args.getJValue()) {
                    result.add(item.pr_str(true));
                }
                return new MalString(result.toString());
            }
        };

    static MalFunction malStr = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                StringJoiner result = new StringJoiner("");

                for (MalType item : args.getJValue()) {
                    result.add(item.pr_str(false));
                }
                return new MalString(result.toString());
            }
        };

    static MalFunction malPrn = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                StringJoiner result = new StringJoiner(" ");

                for (MalType item : args.getJValue()) {
                    result.add(item.pr_str(true));
                }
                System.out.println(result.toString());
                return types.Nil;
            }
        };

    static MalFunction malPrintln = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                StringJoiner result = new StringJoiner(" ");

                for (MalType item : args.getJValue()) {
                    result.add(item.pr_str(false));
                }
                System.out.println(result.toString());
                return types.Nil;
            }
        };

    static MalFunction malReadString = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                assertNArgs(args, 1);
                MalString line = args.get(0).assertType(MalString.class);
                return reader.read_str(line.getJValue());
            }
        };

    static MalFunction malSlurp = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                assertNArgs(args, 1);
                MalString pathname = args.get(0).assertType(MalString.class);

                try {
                    String fileContents = new String(Files.readAllBytes(Paths.get(pathname.getJValue())), StandardCharsets.UTF_8);
                    return new MalString(fileContents);
                } catch(IOException ex) {
                    throw new MalException(ex);
                }
            }
        };

    static MalFunction malAtom = new MalFunction() {
          @Override
          public MalAtom apply(MalList args) throws MalException {
              assertNArgs(args, 1);
              return new MalAtom(args.get(0));
          }
        };

    static MalFunction malAtomP = new MalFunction() {
          @Override
          public MalType apply(MalList args) throws MalException {
              assertNArgs(args,1);
              if (args.get(0) instanceof MalAtom)
                  return types.True;
              else return types.False;
          }
        };

    static MalFunction malDeref = new MalFunction() {
          @Override
          public MalType apply(MalList args) throws MalException {
              assertNArgs(args, 1);
              args.get(0).assertType(MalAtom.class);

              return (MalType)args.get(0).getJValue();
          }
        };

    static MalFunction malReset = new MalFunction() {
          @Override
          public MalType apply(MalList args) throws MalException {
              assertNArgs(args, 2);
              MalAtom atom = args.get(0).assertType(MalAtom.class);
              MalType val = args.get(1).assertType(MalType.class);

              atom.setjValue(val);
              return val;
          }
        };

    static MalFunction malSwap = new MalFunction() {
          @Override
          public MalType apply(MalList args) throws MalException {
              assertMinArgs(args, 2);
              MalAtom atom = args.get(0).assertType(MalAtom.class);
              MalFunction fn = args.get(1).assertType(MalFunction.class);
              LinkedList<MalType> fnArgs = (LinkedList<MalType>)args.subList(2,args.size()).getJValue();
              fnArgs.add(0, atom.getJValue());

              MalType result = fn.apply(new MalList(fnArgs));
              atom.setjValue(result);
              return result;
          }
        };

    static MalFunction malCons = new MalFunction() {
            @Override
            public MalList apply(MalList args) throws MalException {
                assertNArgs(args, 2);

                MalType firstArg = args.get(0);

                MalSequence secondArg = args.get(1).assertType(MalSequence.class);

                List<MalType> oldList = secondArg.getJValue();

                LinkedList<MalType> newList = new LinkedList<>();
                newList.add(firstArg);
                newList.addAll(oldList);
                return new MalList(newList);
            }
        };

    static MalFunction malConcat = new MalFunction() {
            @Override
            public MalList apply(MalList args) throws MalException {
                LinkedList<MalType> newList = new LinkedList<>();

                for (MalType arg : args.getJValue()) {
                    MalSequence argList = arg.assertType(MalSequence.class);
                    newList.addAll(argList.getJValue());
                }
                return new MalList(newList);
            }
        };

    static MalFunction malNth = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                assertNArgs(args, 2);
                MalSequence seq = args.get(0).assertType(MalSequence.class);
                int n = args.get(1).assertType(MalInt.class).getJValue();

                if (n >= seq.size()) throw new MalException("Index out of bounds: " + n + " >= " + seq.size() + ".");

                return seq.get(n);
            }
        };

    static MalFunction malFirst = new MalFunction() {
            @Override
            public MalType apply(MalList args) throws MalException {
                assertNArgs(args, 1);

                MalType firstArg = args.get(0);
                if (firstArg == types.Nil) return firstArg;

                MalSequence seq = firstArg.assertType(MalSequence.class);
                if (seq.size() == 0) return types.Nil;

                return seq.get(0);
            }
        };

    static MalFunction malRest = new MalFunction() {
            @Override
            public MalList apply(MalList args) throws MalException {
                assertNArgs(args, 1);

                MalType firstArg = args.get(0);
                if (firstArg == types.Nil) return new MalList();

                MalSequence seq = firstArg.assertType(MalSequence.class);

                if (seq.size() < 2) return new MalList();
                return seq.subList(1,seq.size());
            }
        };

    static HashMap<MalSymbol,MalFunction> ns = new HashMap<>();

    static {
        ns.put(new MalSymbol("list"),        malList);
        ns.put(new MalSymbol("list?"),       malListP);
        ns.put(new MalSymbol("empty?"),      malEmptyP);
        ns.put(new MalSymbol("count"),       malCount);
        ns.put(new MalSymbol("="),           malEqual);
        ns.put(new MalSymbol("<"),           malLessThan);
        ns.put(new MalSymbol("<="),          malLessThanOrEqual);
        ns.put(new MalSymbol(">"),           malGreaterThan);
        ns.put(new MalSymbol(">="),          malGreaterThanOrEqual);
        ns.put(new MalSymbol("+"),           malAdd);
        ns.put(new MalSymbol("-"),           malSubtract);
        ns.put(new MalSymbol("*"),           malMultiply);
        ns.put(new MalSymbol("/"),           malDivide);
        ns.put(new MalSymbol("pr-str"),      malPrStr);
        ns.put(new MalSymbol("str"),         malStr);
        ns.put(new MalSymbol("prn"),         malPrn);
        ns.put(new MalSymbol("println"),     malPrintln);
        ns.put(new MalSymbol("read-string"), malReadString);
        ns.put(new MalSymbol("slurp"),       malSlurp);
        ns.put(new MalSymbol("atom"),        malAtom);
        ns.put(new MalSymbol("atom?"),       malAtomP);
        ns.put(new MalSymbol("deref"),       malDeref);
        ns.put(new MalSymbol("reset!"),      malReset);
        ns.put(new MalSymbol("swap!"),       malSwap);
        ns.put(new MalSymbol("cons"),        malCons);
        ns.put(new MalSymbol("concat"),      malConcat);
        ns.put(new MalSymbol("nth"),         malNth);
        ns.put(new MalSymbol("first"),       malFirst);
        ns.put(new MalSymbol("rest"),        malRest);

    }
}
