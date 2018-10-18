package mal;

import java.util.HashMap;
import java.util.List;

import mal.types.MalException;
import mal.types.MalList;
import mal.types.MalSymbol;
import mal.types.MalType;

public class env {
    public static class Env {
        Env outer;
        HashMap<MalSymbol,MalType> data = new HashMap<>();

        Env(Env outer) {
            this.outer = outer;
        }

        Env(Env outer, List<MalType> binds, List<MalType> exprs) throws MalException {
            int nSyms = binds.size();
            int nArgs = exprs.size();

            for (int i = 0; i<nSyms; i++) {
                if (!(binds.get(i) instanceof MalSymbol)) throw new MalException("Cannot bind non-symbol: " + binds.get(i).toString());

                if (binds.get(i).getJValue().equals("&")) {
                    if (nSyms == i+1) throw new MalException("Symbol required after `&'.");
                    if (nSyms > i+2) throw new MalException("Multiple symbols after `&'.");
                    this.set((MalSymbol)binds.get(i+1), new MalList(exprs.subList(i, exprs.size())));
                    break;
                }

                if (nArgs <= i) throw new MalException("Wrong number of arguments: expected " + binds.size() + ", received " + nArgs + ".");
                this.set((MalSymbol)binds.get(i), exprs.get(i));
            }

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

        public MalType get(MalSymbol symbol) {
            Env env = this.find(symbol);

            if (env != null) return env.data.get(symbol);
            else return null;
        }
    }
}
