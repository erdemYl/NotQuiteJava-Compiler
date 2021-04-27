// List implementation.
// Creates new objects for keeping values.

int main(){
        List list;
        list = new List();
        list.init();
        list.add(1);
        list.add(2);
        list.add(3);

        printInt(list.find(3));
        return 0;
}

class List {
    boolean empty;
    int head;
    List tail;

    // initialising
    boolean init() {
        empty = true;
        return true;
    }

    // adding element
    int add(int elem) {
        if (empty) {
            empty = false;
            head = elem;
        }
        else {
            tail = new List();
            tail.init();
            tail.add(elem);
        }

        return 1;
    }

    // finding element
    // if found, then returns 1 else returns 0
    int find(int elem) {
        int result;

        if (empty)
            result = 0;
        else
        if (head == elem)
            result = 1;
        else
            result = tail.find(elem);

        return result;
    }
}