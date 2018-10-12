package mal;

import java.io.Console;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mal.types.MalException;
import mal.types.MalHash;
import mal.types.MalInt;
import mal.types.MalKeyword;
import mal.types.MalList;
import mal.types.MalSequence;
import mal.types.MalString;
import mal.types.MalSymbol;
import mal.types.MalType;
import mal.types.MalVector;

public class reader {
    static Console console = System.console();
    static boolean debug = false;

    static private HashMap<String, String> delims;
    static {
        delims = new HashMap<String, String>();
        delims.put("(", ")");
        delims.put("[", "]");
    }

    static private HashMap<String, String> readerMacros;
    static {
        readerMacros = new HashMap<String, String>();
        readerMacros.put("@", "deref");
        readerMacros.put("'", "quote");
        readerMacros.put("`", "quasiquote");
        readerMacros.put("~", "unquote");
        readerMacros.put("~@", "splice-unquote");
    }

    public static class Reader {
        List<String> tokens = new LinkedList<>();
        int position = 0;

        public Reader(List<String> tokens) {
            this.tokens = tokens;
        }

        public String peek() {
            if (position < tokens.size())
                return tokens.get(position);
            else return null;
        }

        public String next() {
            if (position < tokens.size())
                return tokens.get(position++);
            else return null;
        }
    }

    public static List<String> tokenizer(String inputLine) {
        // The regexp is modified a bit so as to require tokens to be consecutive.
        // Not sure if this is really a good idea. Note that I don't actually do
        // anything with this in the while loop.

        // Note also that comments aren't tokenized, contrary to what the Mal guide
        // suggests. This makes it easier to ignore them, especially when they
        // appear after a form.
        String tokenRegexString = "\\G(?:[\\s,]*|;.*$)(~@|[\\[\\]{}\\(\\)'`~^@]|\"(?:\\\\.|[^\\\"])*\"?|[^\\s\\[\\]{}\\('\"`,;\\)]+)";
        Pattern tokenRegex = Pattern.compile(tokenRegexString);
        Matcher inputMatcher = tokenRegex.matcher(inputLine);

        List<String> tokenizedInput = new LinkedList<>();

        String token;

        while (inputMatcher.find()) {
            token = inputMatcher.group(1);
            tokenizedInput.add(token);
        }

        if (debug) console.format("Tokenized input: %s%n", tokenizedInput);

        return tokenizedInput;
    }

    public static MalType read_str(String inputLine) throws MalException {
        Reader tokenized_input;
        MalType result;

        tokenized_input = new Reader(tokenizer(inputLine));
        result = read_form(tokenized_input);

        if (tokenized_input.peek() != null) throw new MalException("Input contains more than one form");

        return result;
    }

    private static MalType read_form(Reader inputForm) throws MalException {
        MalType result = types.Nil;
        String item;

        item = inputForm.peek();

        if (debug) console.format("Item: %s%n", item);

        if (item != null) {
            switch (item) {
            case "(":
            case "[":
                result = read_list(inputForm);
                break;

            case "{":
                result = read_hash(inputForm);
                break;

            case "@":
            case "'":
            case "`":
            case "~":
            case "~@":
                result = read_macro(inputForm);
                break;

            case "^":
                result = read_meta(inputForm);
                break;

            default:
                result = read_atom(inputForm);
                break;
            }
        }
        return result;
    }

    private static MalType read_list(Reader inputForm) throws MalException {
        MalSequence result;
        String item;
        MalType parsedItem;

        String openingDelim = inputForm.next();
        String closingDelim = delims.get(openingDelim);

        switch (openingDelim) {
        case "(": result = new MalList();
            break;
        case "[": result = new MalVector();
            break;
        default: throw new MalException("Not a list delimiter: `" + openingDelim + "'.");
        }

        while (true) {
            item = inputForm.peek();

            if (debug) console.format("List item: %s%n", item);

            if (item == null) throw new MalException("Malformed input: expected `" + closingDelim + "', found EOL.");
            if (item.equals(closingDelim)) {
                inputForm.next(); // Move past the list's closing parenthesis.
                return result;
            }
            if (delims.containsValue(item)) throw new MalException("Malfored input; expected `" + closingDelim + "', found + `" + item + "'.");

            parsedItem = read_form(inputForm);
            result.add(parsedItem);
        }
    }

    private static MalHash read_hash(Reader inputForm) throws MalException {
        MalHash result = new MalHash();
        String key, value;
        MalType parsedKey, parsedValue;

        inputForm.next();

        while(true) {
            key = inputForm.peek();
            if (key.equals("}")) {
                inputForm.next(); // Move past the closing brace.
                return result;
            }

            parsedKey = read_form(inputForm);
            if (!(parsedKey instanceof MalString || parsedKey instanceof MalKeyword))
                throw new MalException("Wrong hash key type (" + parsedKey.getClass() + ").");

            value = inputForm.peek();
            if (value.equals("}")) throw new MalException("Odd number of elements in hash map.");
            parsedValue = read_form(inputForm);

            result.put(parsedKey, parsedValue);
        }
    }

    private static MalList read_macro(Reader inputForm) throws MalException {
        MalList result = new MalList();

        String macro = inputForm.next();

        String resolution = readerMacros.get(macro);
        result.add(new MalSymbol(resolution));

        MalType resolvedForm = read_form(inputForm);

        if (resolvedForm == null) throw new MalException("Incorrect use of reader macro.");

        result.add(resolvedForm);

        return result;
    }

    private static MalList read_meta(Reader inputForm) throws MalException {
        MalList result = new MalList();

        result.add(new MalSymbol("with-meta"));

        inputForm.next();

        MalType data = read_form(inputForm);
        MalType form = read_form(inputForm);

        result.add(form);
        result.add(data);

        return result;
    }

    private static MalType read_atom(Reader inputForm) throws MalException {
        String item = inputForm.next();

        if (debug) console.format("Atom: %s%n", item);

        Pattern
            rxString = Pattern.compile("\"(?:\\\\.|[^\\\"])*\"?"),
            rxComment = Pattern.compile(";.*"),
            rxNumber = Pattern.compile("[+-]?[0-9]+"),
            rxKeyword = Pattern.compile(":[^\\s\\[\\]{}\\('\"`,;\\)]+"),
            rxSymbol = Pattern.compile("[^\\s\\[\\]{}\\('\"`,;\\)]+");

        if (rxString.matcher(item).matches())
            return processString(item);
        else if (rxComment.matcher(item).matches())
            return types.Nil;
        else if (rxNumber.matcher(item).matches())
            return new MalInt(Integer.parseInt(item));
        else if (item.equals("nil"))
            return types.Nil;
        else if (item.equals("false"))
            return types.False;
        else if (item.equals("true"))
            return types.True;
        else if (rxKeyword.matcher(item).matches())
            return new MalKeyword(item);
        else if (rxSymbol.matcher(item).matches())
            return new MalSymbol(item);
        else throw new MalException("Unknown token in input string: `" + item + "'.");
    }

    private static MalString processString(String inputStr) throws MalException {
        Pattern rxString = Pattern.compile("\"((?:\\\\.|[^\\\"])*)\"");
        Matcher matcher = rxString.matcher(inputStr);
        String matchedString;

        // We have to build up the string character by character.
        // Regexp-replacing won't work, because we need to replace a) `\\' with
        // `\' and b) `\n' with `0x0A' (i.e, the newline character). If we do (a)
        // first, the sequence `\\n' is ultimately transformed to `0x0A' (because
        // (a) yields `\n', which (b) then converts to `0x0A'). If we do (b)
        // first, `\\n' is transformed to `\0x0A' (note the slash) by (b), after
        // which (a) doesn't apply anymore. However, the correct result for the
        // sequence `\\n' would be a backslash followed by `n', i.e., `\n',
        // which is a *representation* of the newline character, but not the
        // newline character itself.

        if (matcher.matches()) {
            matchedString = matcher.group(1);
        } else throw new MalException("Invalid string constant: `" + inputStr + "'.");

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < matchedString.length(); i++) {
            char c = matchedString.charAt(i);
            if (c == '\\') {
                char nextChar = matchedString.charAt(++i);
                switch (nextChar) {
                case 'n': result.append('\n');
                    break;
                case '\\': result.append('\\');
                    break;
                case '\"': result.append('\"');
                    break;
                default: result.append('\\');
                    result.append(nextChar);
                }
            }
            else result.append(c);
        }

        String resultString = result.toString();

        if (debug) System.out.println("String: `" + resultString + "'");

        return new MalString(resultString);
    }
}
