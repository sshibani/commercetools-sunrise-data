package com.commercetools.dataimport.orders;

import com.commercetools.dataimport.commercetools.DefaultCommercetoolsJobConfiguration;
import com.commercetools.dataimport.orders.csvline.OrderCsvLineValue;
import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.orders.OrderImportDraft;
import io.sphere.sdk.orders.commands.OrderImportCommand;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.support.SingleItemPeekableItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class OrdersImportJobConfiguration extends DefaultCommercetoolsJobConfiguration {

    private volatile int counter = 0;
    private static final String[] ORDER_CSV_HEADER_NAMES = new String[]{"customerEmail", "orderNumber", "lineitems.variant.sku", "lineitems.price", "lineitems.quantity", "totalPrice"};


    @Bean
    public Job ordersCreateJob(final Step ordersImportStep) {
        return jobBuilderFactory.get("ordersImportJob")
                .start(ordersImportStep)
                .build();
    }

    @Bean
    public Step ordersImportStep(final ItemReader<OrderImportDraft> orderReader,
                                 final ItemWriter<OrderImportDraft> orderWriter) {
        final StepBuilder stepBuilder = stepBuilderFactory.get("ordersImportStep");
        return stepBuilder
                .<OrderImportDraft, OrderImportDraft>chunk(1)
                .reader(orderReader)
                .writer(orderWriter)
                .build();
    }

    @Bean
    protected ItemWriter<OrderImportDraft> orderWriter(final BlockingSphereClient sphereClient) {
        return items -> items.stream()
                .map(OrderImportCommand::of)
                .forEach(sphereClient::executeBlocking);
    }


    @Bean
    @StepScope
    protected OrderImportItemReader orderReader(@Value("#{jobParameters['resource']}") final Resource orderCsvResource) {

        FlatFileItemReader flatFileItemReader = new FlatFileItemReader<>();
        flatFileItemReader.setLineMapper(new DefaultLineMapper<OrderCsvLineValue>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames(ORDER_CSV_HEADER_NAMES);

            }});
            setFieldSetMapper(new BeanWrapperFieldSetMapper<OrderCsvLineValue>() {{
                setTargetType(OrderCsvLineValue.class);
            }});
        }});
        flatFileItemReader.setLinesToSkip(1);
        flatFileItemReader.setResource(orderCsvResource);

        SingleItemPeekableItemReader singleItemPeekableItemReader = new SingleItemPeekableItemReader();
        singleItemPeekableItemReader.setDelegate(flatFileItemReader);

        OrderImportItemReader reader = new OrderImportItemReader();
        reader.setDelegate(singleItemPeekableItemReader);

        return reader;
    }
}
