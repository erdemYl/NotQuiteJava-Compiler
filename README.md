# NotQuiteJava-Compiler
A compiler for "NQJ", an object-oriented language.

This compiler is implemented as a portfolio project in the course "Compiler and Language-Processing Tools WS 2020\2021" of TU Kaiserslautern.
During the course we had programming exercises, with them we started our compiler implementation. At the end we had an "portfolio exam", in which we added improvements to our on hand compiler, so that it supported object-oriented programming paradigms. In the "doc" package, you can find two documentations of the compiler. 

Since the initial "Documentation" accepted as too formal and has lack of insight view of design decisions, I have written another one after the course with name "Frielndly".
Refer to the friendly documentation, since it is an update to older one. Refer to the initial documentation to see my reflections.

The language specification can be found in "NQJ-Grammar". The compiler takes an NQJ program and produces an appropriate LLVM code. 


# The Language
NotQuiteJava is a simple object-oriented language inspired by Java. However it is
different in some ways. It foregoes a lot of complexity around objects and adds functions
(that are not class members). They can be called without an instance of a class and
have no corresponding "this" instance. These functions may be understood as static
member functions of some fixed class that are statically imported.
The semantics of a NotQuiteJava program are given by the semantics of the equivalent Java program with minor exceptions listed in the following. The supported language features are given implicitly by the grammar.

• Overloading of functions and methods is not supported in NotQuiteJava.

• The NotQuiteJava function int printInt(int) is assumed to be present and
can print integers and returns the value unchanged.

• There are no exceptions. When an exception would occur in Java, the program
will terminate with an error code instead.

• The code that is run when the program is executed is given by the int main()
method, which is required to be present. A non-zero return value indicates that
a problem occured.

• There is neither manual memory management nor garbage collection.

• There is no common base class for all objects.
