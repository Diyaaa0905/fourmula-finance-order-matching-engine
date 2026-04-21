package datastructures;

public class RingBuffer<T> {

    private Object[] buffer;
    private int      head;   // index of first element
    private int      tail;   // index of next empty slot
    private int      size;

    public RingBuffer(int initialCapacity) {
        this.buffer = new Object[initialCapacity];
        this.head   = 0;
        this.tail   = 0;
        this.size   = 0;
    }

    public RingBuffer() { this(16); }

    // ── Core operations ──────────────────────────────────────────────────────

    /** Append to the back — O(1) amortized */
    public void enqueue(T item) {
        if (size == buffer.length) grow();
        buffer[tail] = item;
        tail = (tail + 1) % buffer.length;
        size++;
    }

    /** Remove from the front — O(1) */
    @SuppressWarnings("unchecked")
    public T dequeue() {
        if (isEmpty()) throw new IllegalStateException("RingBuffer is empty");
        T item = (T) buffer[head];
        buffer[head] = null;
        head = (head + 1) % buffer.length;
        size--;
        return item;
    }

    /** Peek front without removing — O(1) */
    @SuppressWarnings("unchecked")
    public T peek() {
        if (isEmpty()) return null;
        return (T) buffer[head];
    }

    public boolean isEmpty() { return size == 0; }
    public int     size()    { return size; }

    // ── Growth ───────────────────────────────────────────────────────────────

    private void grow() {
        Object[] newBuffer = new Object[buffer.length * 2];
        for (int i = 0; i < size; i++)
            newBuffer[i] = buffer[(head + i) % buffer.length];
        buffer = newBuffer;
        head   = 0;
        tail   = size;
    }
}
