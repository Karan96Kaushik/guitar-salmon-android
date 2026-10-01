#pragma once

// Lock-free single-producer / single-consumer float ring buffer.
//
// The producer is Oboe's audio callback, which runs on a realtime thread: it may
// not lock, allocate or block. The consumer is the DSP thread. With exactly one
// thread on each side, a pair of atomic indices is enough to synchronise without
// any lock.

#include <atomic>
#include <cstddef>
#include <cstring>
#include <vector>

namespace gs {

class RingBuffer {
public:
    /// Allocates at least `minCapacity` slots, rounded up to a power of two so
    /// index wrapping is a mask rather than a modulo.
    explicit RingBuffer(std::size_t minCapacity) {
        std::size_t capacity = 1;
        while (capacity < minCapacity) capacity <<= 1;
        data_.assign(capacity, 0.0f);
        mask_ = capacity - 1;
    }

    RingBuffer(const RingBuffer&) = delete;
    RingBuffer& operator=(const RingBuffer&) = delete;

    std::size_t capacity() const { return data_.size(); }

    /// Number of samples the consumer can read right now.
    std::size_t available() const {
        const std::size_t w = writeIndex_.load(std::memory_order_acquire);
        const std::size_t r = readIndex_.load(std::memory_order_relaxed);
        return w - r;
    }

    /// Producer side. Copies up to `count` samples in and returns how many were
    /// accepted; a full buffer drops the excess rather than blocking, which keeps
    /// the audio callback bounded even if the DSP thread stalls.
    std::size_t write(const float* src, std::size_t count) {
        const std::size_t w = writeIndex_.load(std::memory_order_relaxed);
        const std::size_t r = readIndex_.load(std::memory_order_acquire);
        const std::size_t room = capacity() - (w - r);
        const std::size_t toWrite = count < room ? count : room;

        for (std::size_t i = 0; i < toWrite; ++i) {
            data_[(w + i) & mask_] = src[i];
        }

        // Release so the consumer that acquires this index also sees the samples.
        writeIndex_.store(w + toWrite, std::memory_order_release);
        return toWrite;
    }

    /// Consumer side. Copies up to `count` samples out and returns how many were
    /// produced.
    std::size_t read(float* dst, std::size_t count) {
        const std::size_t r = readIndex_.load(std::memory_order_relaxed);
        const std::size_t w = writeIndex_.load(std::memory_order_acquire);
        const std::size_t ready = w - r;
        const std::size_t toRead = count < ready ? count : ready;

        for (std::size_t i = 0; i < toRead; ++i) {
            dst[i] = data_[(r + i) & mask_];
        }

        readIndex_.store(r + toRead, std::memory_order_release);
        return toRead;
    }

    /// Discards buffered samples. Only safe while neither side is running.
    void clear() {
        readIndex_.store(0, std::memory_order_relaxed);
        writeIndex_.store(0, std::memory_order_relaxed);
    }

private:
    std::vector<float> data_;
    std::size_t mask_ = 0;
    // Monotonically increasing counts; the difference is the fill level, which
    // stays correct across unsigned wraparound.
    std::atomic<std::size_t> writeIndex_{0};
    std::atomic<std::size_t> readIndex_{0};
};

}  // namespace gs
