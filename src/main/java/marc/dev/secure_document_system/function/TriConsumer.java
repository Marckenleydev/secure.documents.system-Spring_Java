package marc.dev.secure_document_system.function;



@FunctionalInterface
public interface TriConsumer<T, U, V> {
    void accept(T t, U u, V v);
}