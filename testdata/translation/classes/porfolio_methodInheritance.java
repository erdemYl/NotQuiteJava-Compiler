// Tests different object field values and method inheriting.

int main() {
        B b1;
        B b2;

        b1 = new B();
        b2 = new B();

        b1.funB(b2);
        printInt(b1.val);  // 10
        printInt(b2.val);  // 5
        return 0;
        }

class A {
    int val;

    int fun(A a) {
        this.val = 10;
        a.val = 5;
        return 0;
    }
}

class B extends A {
    int funB(B b) {
        fun(b);
        return 0;
    }
}