package mal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;

import mal.env.Env;

public class types {
    public abstract static class MalType {
        Object jValue = null; // The Java value representing the MalType.
        String type = "type"; // The Mal name of the type.

        /**
         * Return the Java value of a MalType.
         */
        public abstract Object getJValue();

        public abstract String pr_str(boolean readably);

        final public String toString() {
            return pr_str(true);
        }

        final public String getType() {
            return type;
        }

        final public <T extends MalType> T assertType(Class<T> type) throws MalException {
            if (type.isInstance(this)) return type.cast(this);
            else throw new MalException(String.format("Wrong argument type: expected %s, received %s.", type.getSimpleName(), this.getClass().getSimpleName()));
        }

        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj.getClass() != this.getClass()) return false;

            MalType that = (MalType)obj;
            return this.getJValue().equals(that.getJValue());
        }
    }

    public static class MalInt extends MalType implements Comparable<MalInt> {
        Integer jValue;

        public MalInt(int value) {
            this.jValue = value;
            type = "int";
        }

        @Override
        public Integer getJValue() {
            return jValue;
        }

        @Override
        public String pr_str(boolean readably) {
            return Integer.toString(jValue);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MalInt)) return false;

            MalInt that = (MalInt)obj;
            return (this.jValue == that.jValue);
        }

        @Override
        public int hashCode() {
            return jValue.hashCode();
        }

        @Override
        public int compareTo(MalInt that) {
            //returns -1 if "this" object is less than "that" object
            //returns 0 if they are equal
            //returns 1 if "this" object is greater than "that" object
            return this.jValue.compareTo(that.jValue);
        }

        boolean isGreaterThan(MalInt that) {
            return this.compareTo(that) > 0;
        }

        boolean isGreaterThanOrEqual(MalInt that) {
            return this.compareTo(that) >= 0;
        }

        boolean isLessThan(MalInt that) {
            return this.compareTo(that) < 0;
        }

        boolean isLessThanOrEqual(MalInt that) {
            return this.compareTo(that) <= 0;
        }
    }

    public static abstract class MalSequence extends MalType {
        List<MalType> jValue;

        @Override
        public List<MalType> getJValue() {
            return jValue;
        }

        public int size() {
            return jValue.size();
        }

        public void add(MalType e) {
            jValue.add(e);
        }

        public void addAll(MalSequence elems) {
            jValue.addAll(elems.getJValue());
        }

        public MalType get(int i) {
            return jValue.get(i);
        }

        // Note: subList always returns a list, even when called on a vector.
        public MalList subList(int beg, int end) {
            return new MalList(jValue.subList(beg, end));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MalSequence)) return false;

            MalSequence that = (MalSequence)obj;
            if (that.size() != this.size()) return false;

            for (int i=0; i<this.size(); i++) {
                if (!this.get(i).equals(that.get(i))) return false;
            }
            return true;
        }

        public int length() {
            return jValue.size();
        }
    }

    public static class MalList extends MalSequence {
        public MalList() {
            this.jValue = new LinkedList<MalType>();
            type = "list";
        }

        public MalList(MalType item) {
            this.jValue = new LinkedList<MalType>();
            type = "list";
            this.jValue.add(item);
        }

        public MalList(List<MalType> items) {
            this.jValue = items;
            type = "list";
        }

        @Override
        public String pr_str(boolean readably) {
            StringJoiner result = new StringJoiner(" ", "(", ")");

            for(MalType item : jValue) {
                result.add(item.pr_str(readably));
            }

            return result.toString();
        }
    }

    private static class MalNil extends MalType {

        public MalNil() {
            super();
            type = "symbol";
        }

        @Override
        public String pr_str(boolean readably) {
            return "nil";
        }

        @Override
        public Object getJValue() {
            return null;
        }
    }

    public static final MalNil Nil = new MalNil();

    public static class MalVector extends MalSequence {
        public MalVector() {
            this.jValue = new ArrayList<MalType>();
            type = "vector";
        }

        public MalVector(List<MalType> items) {
            this.jValue = items;
            type = "vector";
        }

        @Override
        public String pr_str(boolean readably) {
            StringJoiner result = new StringJoiner(" ", "[", "]");

            for(MalType item : jValue) {
                result.add(item.pr_str(readably));
            }

            return result.toString();
        }

        public MalVector subVector(int beg, int end) {
            return new MalVector(jValue.subList(beg, end));
        }
    }

    public static class MalHash extends MalType {
        HashMap<MalType,MalType> jValue;

        public MalHash() {
            jValue = new HashMap<MalType,MalType>();
            type = "hash";
        }

        public MalHash(HashMap<MalType,MalType> h) {
            jValue = h;
            type = "hash";
        }

        public void put(MalType k, MalType v) {
            jValue.put(k, v);
        }

        public MalType get(MalType k) {

            MalType val = jValue.get(k);
            if (val == null) return Nil;
            else return val;
        }

        public MalList keys() {
            LinkedList<MalType> keysList = new LinkedList<>(jValue.keySet());
            return new MalList(keysList);
        }

        public MalList values() {
            LinkedList<MalType> keysList = new LinkedList<>(jValue.values());
            return new MalList(keysList);
        }

        public MalType delete(MalType k) {
            jValue.remove(k);
            return null;
        }

        public MalHash copy() {
            HashMap<MalType,MalType> newMap = (HashMap<MalType,MalType>)this.jValue.clone();

            return new MalHash(newMap);
        }

        @Override
        public HashMap<MalType,MalType> getJValue() {
            return jValue;
        }

        @Override
        public String pr_str(boolean readably) {
            StringJoiner result = new StringJoiner(" ", "{", "}");

            for (HashMap.Entry<MalType,MalType> entry : jValue.entrySet()) {
                MalType key = entry.getKey();
                MalType value = entry.getValue();
                result.add(key.pr_str(readably) + " " + value.pr_str(readably));
            }

            return result.toString();
        }
    }

    public static class MalString extends MalType {
        String jValue;

        public MalString(String jValue) {
            this.jValue = jValue;
            type = "string";
        }

        @Override
        public String getJValue() {
            return jValue;
        }

        @Override
        public int hashCode() {
            return jValue.hashCode();
        }

        @Override
        public String pr_str(boolean readably) {
            if (readably == false) return jValue;
            else {
                String result;

                result = jValue.replace("\\", "\\\\");
                result = result.replace("\n", "\\n");
                result = result.replace("\"", "\\\"");

                return "\"" + result + "\"";
            }
        }

        public MalString prepend(String prefix) {
            return new MalString(prefix + this.jValue);
        }

        public int length() {
            return jValue.length();
        }
    }

    public static class MalSymbol extends MalType implements Comparable<MalSymbol> {
        String jValue;

        public MalSymbol(String name) {
            this.jValue = name;
            type = "symbol";
        }

        public MalSymbol(MalString name) {
            this.jValue = name.getJValue();
            type = "symbol";
        }

        @Override
        public String getJValue() {
            return jValue;
        }

        @Override
        public String pr_str(boolean readably) {
            return jValue;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MalSymbol)) return false;

            MalSymbol that = (MalSymbol)obj;
            return this.jValue.equals(that.jValue);
        }

        @Override
        public int hashCode() {
            return jValue.hashCode();
        }

        @Override
        public int compareTo(MalSymbol that) {
            //returns -1 if "this" object is less than "that" object
            //returns 0 if they are equal
            //returns 1 if "this" object is greater than "that" object
            return this.jValue.compareTo(that.jValue);
        }
    }

    public static class MalKeyword extends MalType implements Comparable<MalKeyword> {
        String jValue;

        public MalKeyword(String name) {
            this.jValue = name;
            type = "keyword";
        }

        public MalKeyword(MalString name) {
            this.jValue = name.getJValue();
            type = "keyword";
        }

        @Override
        public String getJValue() {
            return jValue;
        }

        @Override
        public String pr_str(boolean readably) {
            return jValue;
        }

        @Override
        public int hashCode() {
            return jValue.hashCode();
        }

        @Override
        public int compareTo(MalKeyword that) {
            return this.jValue.compareTo(that.jValue);
        }
    }

    private static class MalBoolean extends MalType {
        Boolean jValue;

        public MalBoolean(boolean value) {
            this.jValue = value;
            type = "boolean";
        }

        @Override
        public Boolean getJValue() {
            return jValue;
        }

        @Override
        public String pr_str(boolean readably) {
            return jValue.toString();
        }
    }

    public static final MalBoolean True = new MalBoolean(true);
    public static final MalBoolean False = new MalBoolean(false);

    @FunctionalInterface
    static abstract interface MalCallable {
        public MalType apply(MalList args) throws MalException;
    }

    public static abstract class MalFunction extends MalType implements MalCallable, Cloneable {
        MalType metadata = Nil;

        public MalFunction() {
            type = "function";
        }

        @Override
        public Object getJValue() {
            return this;
        }

        public MalType getMeta() {
            return this.metadata;
        }

        public void setMeta(MalType data) {
            this.metadata = data;
        }

        @Override
        public String pr_str(boolean readably) {
            return "#<function@" + this.hashCode() + ">";
        }

        @Override
        public MalFunction clone() {
            try {
                return (MalFunction)super.clone();
            } catch (CloneNotSupportedException e) {
                // This feels weird. Basically, I'm creating the clone manually,
                // so why would I try calling super.clone() first? Note, though,
                // that this clone always returns MalType, while some built-in
                // functions return subtypes of MalType.
                MalFunction newFn = new MalFunction() {
                        @Override
                        public MalType apply(MalList args) throws MalException {
                            return this.apply(args);
                        }
                    };
                return newFn;
            }
        }
    }

    public static class MalUserFunction extends MalFunction implements Cloneable {
        boolean is_macro = false;

        MalType ast;
        MalSequence params;
        Env env;
        MalFunction fn;

        public MalUserFunction() {
            type = "function";
        }

        @Override
        public Object getJValue() {
            return this;
        }

        @Override
        public String pr_str(boolean readably) {
            return "#<function@" + this.hashCode() + ">";
        }

        public void setAst(MalType ast) {
            this.ast = ast;
        }

        public MalType getAst() {
            return this.ast;
        }

        public void setParams(MalSequence params) {
            this.params = params;
        }

        public MalSequence getParams() {
            return this.params;
        }

        public void setEnv(Env env) {
            this.env = env;
        }

        public Env getEnv() {
            return this.env;
        }

        public void setFn(MalFunction fn) {
            this.fn = fn;
        }

        public MalFunction getFn() {
            return this.fn;
        }

        public boolean isMacro() {
            return is_macro;
        }

        public void setMacro() {
            this.is_macro = true;
        }

        @Override
        public MalType apply(MalList args) throws MalException {
            return fn.apply(args);
        }

        @Override
        public MalUserFunction clone() {
            MalUserFunction newFn = new MalUserFunction();

            newFn.is_macro = this.is_macro;
            newFn.ast = this.ast;
            newFn.params = this.params;
            newFn.env = this.env;
            newFn.fn = this.fn;
            newFn.metadata = this.metadata;

            return newFn;
        }
    }

    public static class MalAtom extends MalType {
        MalType jValue;

        public MalAtom(MalType val) {
            type = "atom";
            this.jValue = val;
        }

        @Override
        public MalType getJValue() {
            return jValue;
        }

        public void setjValue(MalType val) {
            this.jValue = val;
        }

        @Override
        public String pr_str(boolean readably) {
            return "(atom " + jValue.pr_str(readably) + ")";
        }

    }

    public static class MalException extends Exception {
        private static final long serialVersionUID = 3809884595479541313L;

        MalType errVal = null;

        public MalException() {
            super();
        }

        public MalException(String message) {
            super(message);
            this.errVal = new MalString(message);
        }

        public MalException(MalType value) {
            super(value.pr_str(true));
            this.errVal = value;
        }

        public MalException(String message, Throwable cause) {
            super(message, cause);
        }

        public MalException(Throwable cause) {
            super(cause);
        }

        public MalType getErrVal() {
            return this.errVal;
        }
    }

    public static class MalError extends MalType {
        MalType errVal;

        public MalError(MalType value) {
            type = "error";
            this.errVal = value;
        }

        @Override
        public Object getJValue() {
            return new MalException(errVal);
        }

        @Override
        public String pr_str(boolean readably) {
            return errVal.pr_str(readably);
        }
    }
}
