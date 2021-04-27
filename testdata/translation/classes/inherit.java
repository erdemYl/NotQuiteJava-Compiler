int main() {
    List list;
    list = new List();
    list.add(1);
    list.add(2);
    list.add(4);
    list.add(1);
    list.add(2);
    list.add(4);
    printer(list.tail.elements);
    return 0;
}

int printer(int[] given) {
    int i;
    i = 0;
    while (i < given.length) {
        printInt(given[i]);
        i = i + 1;
    }
    return 1;
}

class Tail {
    int[] elements;

    int add(int elem) {
        if (elements == null) {
            elements = new int[1];
            elements[0] = elem;
        }
        else {
            int[] other;
            int size;
            size = elements.length;
            other = new int[size + 1];

            int i;
            i = 0;
            while(i < size) {
                other[i] = elements[i];
                i = i + 1;
            }
            other[size] = elem;
            elements = other;
        }

        return 1;
    }

    int[] replace(int[] array) {
        int[] other;
        int size;
        size = array.length;
        other = new int[size + 1];

        int i;
        i = 0;
        while(i < size) {
            other[i] = array[i];
            i = i + 1;
        }

        return other;
    }
}

class List extends Tail {
    int head;
    Tail tail;

    int add(int elem) {
        if (head == 0) {
            head = elem;
            tail = new Tail();
        }

        else {
            tail.add(elem);
        }
        return 1;
    }

    int getSize() {
        return 1 + tail.elements.length;
    }
}