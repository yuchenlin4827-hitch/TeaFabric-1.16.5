package org.lwjgl.system;

public interface Pointer {
    int POINTER_SIZE = 8;
    boolean BITS64 = true;
    boolean BITS32 = false;

    long address();

    abstract class Default implements Pointer {
        protected long address;

        protected Default(long address) {
            this.address = address;
        }

        @Override
        public long address() {
            return address;
        }
    }
}
