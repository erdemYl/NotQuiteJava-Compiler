package analysis;

import analysis.ClassRepresentations.ClassType;

/**
 * Type class for handling the formal types of NotQuiteJava and the sub-type relation.
 * Provides static members for basic types.
 * This class is extended by "ClassType" for class implementations.
 */
public abstract class Type {

    public abstract boolean isSubtypeOf(Type other);

    public boolean isEqualToType(Type other) {
        return this.isSubtypeOf(other) && other.isSubtypeOf(this);
    }

    public static final Type INT = new Type() {

        @Override
        public boolean isSubtypeOf(Type other) {
            return other == this || other == ANY;
        }

        @Override
        public String toString() {
            return "int";
        }
    };

    public static final Type BOOL = new Type() {

        @Override
        public boolean isSubtypeOf(Type other) {
            return other == this || other == ANY;
        }

        @Override
        public String toString() {
            return "boolean";
        }
    };

    public static final Type NULL = new Type() {

        @Override
        public boolean isSubtypeOf(Type other) {
            return other == this
                    || other instanceof ArrayType
                    || other instanceof ClassType
                    || other == ANY;
        }

        @Override
        public String toString() {
            return "null";
        }
    };

    public static final Type ANY = new Type() {

        @Override
        public boolean isSubtypeOf(Type other) {
            return true;
        }

        @Override
        public String toString() {
            return "any";
        }
    };


}
