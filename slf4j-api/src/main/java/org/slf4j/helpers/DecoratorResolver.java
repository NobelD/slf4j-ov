package org.slf4j.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType, OptionalAssignedToNull")
public interface DecoratorResolver {
    /**
     *
     * @param part all the parts divided
     * @param index the current index
     * @return null to conserve the path,
     */
    Result resolve(String[] part, int index);

    Tree asTree();

    End asEnd();

    String part();

    void part(String value);

    boolean conserve();

    void conserve(boolean value);

    static boolean registerTo(Map<String, DecoratorResolver> map, String start, String replacement) {
        DecoratorResolver.Parsed result = DecoratorResolver.create(start, replacement);
        DecoratorResolver resolver = result.resolver();
        DecoratorResolver saved = map.compute(result.name(), (k, v) -> {
            if (v == null) {
                return resolver;
            } else {
                return DecoratorResolver.merge(v, resolver);
            }
        });
        return saved == resolver;
    }

    static String resolve(Map<String, DecoratorResolver> map, String name) {
        String[] split = name.split("\\.");
        String last = split[split.length-1];
        DecoratorResolver resolver;
        if (split.length == 1 && map.containsKey("*")) {
            resolver = map.get("*");
        } else {
            resolver = map.get(split[0]);
        }
        Result result = resolver == null ? null : resolver.resolve(split, 1);
        if (result != null) {
            String start = result.replacement();
            if (result.conserve()) {
                StringBuilder builder = new StringBuilder();
                if (start != null) {
                    builder.append(start).append(".");
                }
                for (int i = 0; i < result.index(); i++) {
                    builder.append(split[i]);
                }
                name = builder.toString();
            } else {
                name = start == null ? last : (start + '.' + last);
            }
        }
        return name;
    }

    static DecoratorResolver merge(DecoratorResolver first, DecoratorResolver other) {
        if (first instanceof End && other instanceof End) {
            End e1 = first.asEnd();
            End e2 = other.asEnd();
            e1.part(e2.part());
            e1.conserve(e2.conserve());
        }
        Tree tree = first.asTree();
        if (other instanceof Tree) {
            Tree tOther = other.asTree();
            if (tOther.treePart() != null) {
                tree.treePart(tOther.treePart());
                tree.conserve(tOther.conserve);
            }
            for (Map.Entry<String, DecoratorResolver> eOther : tOther.resolvers.entrySet()) {
                tree.resolvers.merge(eOther.getKey(), eOther.getValue(), DecoratorResolver::merge);
            }
        } else if (other instanceof End) {
            End e = other.asEnd();
            tree.part(e.part());
            tree.conserve(e.conserve());
        }
        return tree;
    }

    static Parsed create(String part, String replacement) {
        String replace = replacement == null ? null : replacement.replace(" ", "");
        if (replace != null && replace.trim().isEmpty()) throw new IllegalArgumentException("Cannot parse empty string");

        String[] split = part.split("\\.");
        for (String s : split) {
            if (s.trim().isEmpty()) {
                throw new IllegalArgumentException("Replacement must not contain empty values across their values");
            }
        }

        int current = split.length - 1;
        String str = split[current];
        boolean conserve, wildcard;
        if (str.equals("*")) {
            str = split[--current];
            current--;
            conserve = false;
            wildcard = true;
        } else if (str.equals("*~")) {
            str = split[--current];
            current--;
            conserve = true;
            wildcard = true;
        } else {
            conserve = false;
            wildcard = false;
        }

        DecoratorResolver resolver;
        if (wildcard) {
            resolver = new Tree(null, Optional.ofNullable(replace), conserve);
        } else {
            resolver = new End(replace);
        }
        for (int i = current; i >= 0; i--) {
            Map<String, DecoratorResolver> toSet = new HashMap<>();
            toSet.put(str, resolver);
            resolver = new Tree(toSet, (Optional<String>) null, false);
            str = split[i];
        }
        return new Parsed(str, resolver);
    }

    class Tree implements DecoratorResolver {
        private final Map<String, DecoratorResolver> resolvers;
        private Optional<String> part;
        private boolean conserve;

        public Tree(Map<String, DecoratorResolver> resolvers, Optional<String> part, boolean conserve) {
            this.resolvers = resolvers == null ? new HashMap<>() : new HashMap<>(resolvers);
            this.part = part;
            this.conserve = conserve;
        }

        public Tree(Map<String, DecoratorResolver> resolvers, String part, boolean conserve) {
            this(resolvers, Optional.ofNullable(part), conserve);
        }

        public Tree(Map<String, DecoratorResolver> resolvers) {
            this(resolvers, (Optional<String>) null, false);
        }

        @Override
        public Result resolve(String[] part, int index) {
            DecoratorResolver v = part.length > index ? resolvers.get(part[index]) : null;
            return v != null ? v.resolve(part, index+1) : this.part == null ? null : new Result(this.part.orElse(null), conserve, index);
        }

        public String part() {
            return part == null ? null : part.orElse(null);
        }

        public void part(String part) {
            this.part = Optional.ofNullable(part);
        }

        public Optional<String> treePart() {
            return part;
        }

        public void treePart(Optional<String> part) {
            this.part = part;
        }

        public boolean conserve() {
            return conserve;
        }

        public void conserve(boolean conserve) {
            this.conserve = conserve;
        }

        @Override
        public Tree asTree() {
            return this;
        }

        @Override
        public End asEnd() {
            throw new IllegalStateException("Unable to create end part");
        }

        @Override
        public String toString() {
            return "Tree{" +
                    "resolvers=" + resolvers +
                    ", part=" + part +
                    ", conserve=" + conserve +
                    '}';
        }
    }

    class End implements DecoratorResolver {
        private String part;

        public End(String part) {
            this.part = part;
        }

        public String part() {
            return part;
        }

        public void part(String part) {
            this.part = part;
        }

        public boolean conserve() {
            return false;
        }

        public void conserve(boolean conserve) {
        }

        @Override
        public Result resolve(String[] part, int index) {
            return part.length-1 != index ? new Result(index) : new Result(this.part, false, index);
        }

        @Override
        public Tree asTree() {
            return new Tree(null, this.part, false);
        }

        @Override
        public End asEnd() {
            return this;
        }

        @Override
        public String toString() {
            return "End{" +
                    "part='" + part + '\'' +
                    '}';
        }
    }

    /**
     * The result of an evaluation to a class name.<br>
     * Case 1 - Trees may return null (result) as no value was found.<br>
     * Case 2 - Trees and ends may return an empty optional to not add any replacement and keep the last name only. (Ex: List)<br>
     * Case 3 - Trees and ends may return an optional with a value to replace before the name. (Ex: Java.List)<br>
     * <br>
     * Also they can return a boolean to conserve the exceed values if matched. (Ex: Jetbrains.annotations.Nullable)
     */
    final class Result {
        private final String replacement;
        private final boolean conserve;
        private final int index;

        /**
         * @param replacement the replacement
         * @param conserve    if exceed should be conserved
         */
        public Result(String replacement, boolean conserve, int index) {
            this.replacement = replacement;
            this.conserve = conserve;
            this.index = index;
        }

        public Result(int index) {
            this(null, false, index);
        }

        public String replacement() {
            return replacement;
        }

        public boolean conserve() {
            return conserve;
        }

        public int index() {
            return index;
        }
    }

    final class Parsed {
        private final String name;
        private final DecoratorResolver resolver;

        Parsed(String name, DecoratorResolver resolver) {
            this.name = name;
            this.resolver = resolver;
        }

        public String name() {
            return name;
        }

        public DecoratorResolver resolver() {
            return resolver;
        }
    }
}
