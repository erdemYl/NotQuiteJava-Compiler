// tests two different objects

int main() {
        A a;
        a = null;
        a = new A();
        a.key = 10;
        printInt(a.key); // 10
        a = new A();
        a.key = 5;
        printInt(a.key); // 5
        return 0;
        }

class A {
    int key;
}