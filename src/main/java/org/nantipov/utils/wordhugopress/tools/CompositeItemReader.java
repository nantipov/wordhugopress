package org.nantipov.utils.wordhugopress.tools;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.util.Collection;
import java.util.Iterator;

public class CompositeItemReader<T> implements ItemReader<T>, ItemStream {

    private final Collection<ItemReader<T>> readers;
    private final Iterator<ItemReader<T>> iterator;
    private ItemReader<T> currentReader;

    private CompositeItemReader(Collection<ItemReader<T>> readers) {
        this.readers = readers;
        this.iterator = readers.iterator();
    }

    public static <T> CompositeItemReader<T> of(Collection<ItemReader<T>> readers) {
        return new CompositeItemReader<T>(readers);
    }

    @Override
    public T read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        while (true) {
            T value = (currentReader != null) ? currentReader.read() : null;
            if (value != null) {
                return value;
            } else {
                if (iterator.hasNext()) {
                    currentReader = iterator.next();
                } else {
                    return null;
                }
            }
        }
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        readers.stream()
               .filter(reader -> reader instanceof ItemStream)
               .map(reader -> (ItemStream) reader)
               .forEach(reader -> reader.open(executionContext));
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        readers.stream()
               .filter(reader -> reader instanceof ItemStream)
               .map(reader -> (ItemStream) reader)
               .forEach(reader -> reader.update(executionContext));
    }

    @Override
    public void close() throws ItemStreamException {
        readers.stream()
               .filter(reader -> reader instanceof ItemStream)
               .map(reader -> (ItemStream) reader)
               .forEach(ItemStream::close);
    }
}
