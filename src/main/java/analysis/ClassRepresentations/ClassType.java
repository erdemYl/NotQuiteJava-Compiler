package analysis.ClassRepresentations;


import analysis.Type;

/**
 * Class extension for types.
 * Represents the subtype relations of a class.
 */
public class ClassType extends Type {
    private ClassRef thisClass;
    private ClassRef extClass;

    public ClassType(ClassRef refClass, ClassRef refExt) {
        thisClass = refClass;
        extClass = refExt;
    }

    public ClassRef getClassRef() {
        return thisClass;
    }

    public ClassRef getExtClassRef() {
        return extClass;
    }

    /**
     * One class is subtype of its direct super class,
     * or super class' super classes.
     */
    @Override
    public boolean isSubtypeOf(Type other) {
        // no super class
        if (this.extClass == null)
            return classTypeEqualTo(other);

        else {
            return classTypeEqualTo(other)
                    || extClass.getType().isSubtypeOf(other);
        }
    }

    /**
     * Classes are equal to same classes.
     */
    private boolean classTypeEqualTo(Type other) {
        // class is equal to the same class
        if (other instanceof ClassType) {
            String otherName = ((ClassType) other).getClassRef().getName();
            return thisClass.getName().equals(otherName);
        }

        return false;
    }

    public String toString() {
        return thisClass.getName();
    }
}