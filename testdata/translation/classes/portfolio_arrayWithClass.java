// tests arrays with class

int main() {
        A[] arr;
        A a;
        arr = new A[10];
        arr[0] = new A();
        a = arr[0];
        printInt(a.a); // 0
        return 0;
        }

class A {
    int a;
}