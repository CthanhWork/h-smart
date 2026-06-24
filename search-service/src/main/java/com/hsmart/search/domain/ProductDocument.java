package com.hsmart.search.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products_index")
@Setting(settingPath = "/elasticsearch/products-index-settings.json")
public class ProductDocument {

    @Id
    @Field(type = FieldType.Long)
    private Long id;

    @Field(type = FieldType.Text, analyzer = "hsmart_text_analyzer", searchAnalyzer = "hsmart_text_analyzer")
    private String title;

    @Field(type = FieldType.Text, analyzer = "hsmart_text_analyzer", searchAnalyzer = "hsmart_text_analyzer")
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Text, analyzer = "hsmart_text_analyzer", searchAnalyzer = "hsmart_text_analyzer")
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;
}
