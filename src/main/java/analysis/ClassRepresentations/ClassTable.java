package analysis.ClassRepresentations;

import analysis.Analysis;
import notquitejava.ast.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents all declared classes in a program, after the name analysis of them.
 *
 * Used method of name analysis is as follows:
 *   First, analyse class names.
 *   Second, analyse extension names.
 *   Last, represent available class references in a table.
 *
 */
public class ClassTable {
    private final HashMap<String, ClassRef> table = new HashMap<>();
    private final HashMap<String, NQJClassDecl> helperTable = new HashMap<>();
    private final Analysis analysis;

    public ClassTable(NQJClassDeclList list, Analysis analysis) {
        this.analysis = analysis;
        Set<String> names = new HashSet<>();

        // First
        for (NQJClassDecl c : list) {
            if (!names.add(c.getName())) {
                analysis.addError(c, "Class with name " + c.getName()
                        + " is already defined.");
            }
            else helperTable.put(c.getName(), c);
        }
        // Second
        checkExtensions(list);
        // a part of Second
        checkCycles(list);
        // Last
        turnToRefs(list);
    }

    // from "helperTable" to "table"
    private void turnToRefs(NQJClassDeclList list) {
        for (NQJClassDecl c : list) {
            String name = c.getName();
            if (helperTable.containsKey(name)) {
                table.put(name, new ClassRef(name, c));
            }
        }
    }

    /**
     * Examines for all classes their super classes.
     */
    private void checkExtensions(NQJClassDeclList l) {
        for (NQJClassDecl c : l) {
            String name = c.getName();

            if (c.getExtended() instanceof NQJExtendsClass) {
                String extName = ((NQJExtendsClass) c.getExtended()).getName();

                if (!helperTable.containsKey(extName)) {
                    analysis.addError(c, "Class extension is a not declared class with name "
                            + extName + ".");
                    c.setExtended(NQJ.ExtendsNothing());
                }
                else {
                    c.setDirectSuperClass(helperTable.get(extName));
                }
            }
        }
    }

    private void checkCycles(NQJClassDeclList l) {
        for (NQJClassDecl decl : l) {
            NQJClassDecl extClass = decl.getDirectSuperClass();

            if (extClass == null)
                continue;

            else {
                chainDependency(decl, new HashSet<>());
            }
        }
    }

    /**
     * Checks cyclic dependencies in classes
     * @param thisClass is the given class
     * @param extensions is a set including the extended classes
     */
    private void chainDependency(NQJClassDecl thisClass, Set<NQJClassDecl> extensions) {
        if (thisClass == null)
            return;

        // found cyclic dependency
        if (!extensions.add(thisClass)) {
            extensions.forEach(
                    decl -> analysis.addError(decl, "Cyclic dependency involving: "
                            + extensions
                            .stream()
                            .map(NQJClassDecl::getName)
                            .collect(Collectors.toList())
                            .toString()
            ));
            extensions.forEach(
                    decl -> decl.setExtended(NQJ.ExtendsNothing())
            );
            extensions.forEach(
                    decl -> decl.setDirectSuperClass(null)
            );
        }
        else chainDependency(thisClass.getDirectSuperClass(), extensions);
    }

    public ClassRef lookupClass(String s) {
        return table.get(s);
    }

}