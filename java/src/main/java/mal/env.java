package mal;

import java.util.HashMap;

import mal.types.MalException;
import mal.types.MalSymbol;
import mal.types.MalType;

public class env {
    public static class Env {
        Env outer;
        HashMap<MalSymbol,MalType> data = new HashMap<>();

        Env(Env outer) {
            this.outer = outer;
        }

        public void set(MalSymbol symbol, MalType value) {
            data.put(symbol, value);
        }

        public Env find(MalSymbol symbol) {
            if (data.containsKey(symbol)) return this;
            else if (outer == null) return null;
            else return outer.find(symbol);
        }

        public MalType get(MalSymbol symbol) throws MalException {
            Env env = this.find(symbol);

            if (env != null) return env.data.get(symbol);
            else throw new MalException("Symbol value is void: " + symbol.toString());
        }
    }
}
