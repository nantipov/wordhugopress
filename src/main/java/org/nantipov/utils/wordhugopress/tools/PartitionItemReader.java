package org.nantipov.utils.wordhugopress.tools;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Splits stream into partitions by certain criteria.
 */
public class PartitionItemReader<I, O> implements ItemStreamReader<O> {

    private final ItemReader<I> inputReader;
    private final BiPredicate<I, I> samePartitionPredicate;
    private final Function<List<I>, O> mapper;
    private final long maxItemsInBuffer; // 0 - unlimited

    private final List<I> buffer = new LinkedList<>();
    private I previousItem = null;

    private boolean isInputReadClosed = false;

    public PartitionItemReader(ItemReader<I> inputReader, BiPredicate<I, I> samePartitionPredicate,
                               Function<List<I>, O> mapper, long maxItemsInBuffer) {
        this.inputReader = inputReader;
        this.samePartitionPredicate = samePartitionPredicate;
        this.mapper = mapper;
        this.maxItemsInBuffer = maxItemsInBuffer;
    }

    @Override
    public O read() throws Exception {
        if (isInputReadClosed) {
            return null;
        }

        I inputItem;
        while (isSamePartition(inputItem = inputReader.read(), buffer.size())) {
            previousItem = inputItem;
            buffer.add(inputItem);
        }

        previousItem = inputItem;

        if (inputItem == null) {
            isInputReadClosed = true;
        }

        O outputItem;
        if (!buffer.isEmpty()) {
            List<I> bufferedItems = new ArrayList<>(buffer);
            buffer.clear();

            if (inputItem != null) {
                buffer.add(inputItem);
            }

            outputItem = mapper.apply(bufferedItems);
        } else {
            outputItem = null;
        }

        return outputItem;
    }

    private boolean isSamePartition(I item, long bufferSize) {
        return item != null &&
               (maxItemsInBuffer <= 0 || bufferSize < maxItemsInBuffer) &&
               (this.previousItem == null || samePartitionPredicate.test(previousItem, item));
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (inputReader instanceof ItemStream) {
            ((ItemStream) inputReader).open(executionContext);
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        if (inputReader instanceof ItemStream) {
            ((ItemStream) inputReader).update(executionContext);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        if (inputReader instanceof ItemStream) {
            ((ItemStream) inputReader).close();
        }
    }
}
