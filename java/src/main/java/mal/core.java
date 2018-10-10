package mal;

import java.util.HashMap;

import mal.types.MalException;
import mal.types.MalFunction;
import mal.types.MalInt;
import mal.types.MalList;
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

    static HashMap<MalSymbol,MalFunction> ns = new HashMap<>();

    static {
        ns.put(new MalSymbol("+"), malAdd);
        ns.put(new MalSymbol("-"), malSubtract);
        ns.put(new MalSymbol("*"), malMultiply);
        ns.put(new MalSymbol("/"), malDivide);
    }
}
