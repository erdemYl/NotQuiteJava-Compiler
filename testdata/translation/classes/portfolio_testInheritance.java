// tests field and method inheritance

int main() {
        ABC k;
        AB m;
        k = new ABC();
        m = new AB();
        printInt(k.fun1()); // 5
        printInt(k.fun2()); // 10
        printInt(k.fun3()); // 0
        printInt(k.a);     // 10
        printInt(m.funktion()); // 10
        printInt(m.a); // 10
        m = new AB();
        printInt(m.a); // 0
        return 0;
}

class ABC {
    int a;

    int fun1() {
        a = 5;
        return a;
    }

    int fun2() {
        a = 10;
        return a;
    }

    int fun3() {
        int a;
        a = 0;
        return a;
    }

    int fun4() {
        return fun2();
    }
}

class AB extends ABC {
    int funktion() {
        return fun4();
    }
}
