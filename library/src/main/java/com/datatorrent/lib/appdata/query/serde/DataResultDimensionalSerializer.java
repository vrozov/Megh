/**
 * Copyright (c) 2016 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.datatorrent.lib.appdata.query.serde;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.apex.malhar.lib.dimensions.DimensionsDescriptor;

import com.google.common.collect.Maps;

import com.datatorrent.lib.appdata.gpo.GPOMutable;
import com.datatorrent.lib.appdata.schemas.DataResultDimensional;
import com.datatorrent.lib.appdata.schemas.Fields;
import com.datatorrent.lib.appdata.schemas.FieldsAggregatable;
import com.datatorrent.lib.appdata.schemas.Message;
import com.datatorrent.lib.appdata.schemas.Result;
import com.datatorrent.lib.appdata.schemas.ResultFormatter;
import com.datatorrent.lib.appdata.schemas.Type;

/**
 * This class is used to serialize {@link DataResultDimensional} objects.
 *
 * @since 3.1.0
 */

public class DataResultDimensionalSerializer implements CustomMessageSerializer
{
  public static final String ALL = "ALL";

  /**
   * Constructor used to instantiate deserializer in {@link MessageDeserializerFactory}.
   */
  public DataResultDimensionalSerializer()
  {
  }

  @Override
  public String serialize(Message result, ResultFormatter resultFormatter)
  {
    try {
      return serializeHelper(result, resultFormatter);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String serializeHelper(Message result, ResultFormatter resultFormatter) throws Exception
  {
    DataResultDimensional dataResult = (DataResultDimensional)result;

    JSONObject jo = new JSONObject();

    jo.put(Result.FIELD_ID, dataResult.getId());
    jo.put(Result.FIELD_TYPE, dataResult.getType());

    JSONArray data = new JSONArray();
    jo.put(Result.FIELD_DATA, data);

    //dataResult.getQuery().g

    boolean hasTime = dataResult.getQuery().isHasTime();

    FieldsAggregatable fieldsAggregatable = dataResult.getQuery().getFieldsAggregatable();
    Fields nonAggregatedFields = fieldsAggregatable.getNonAggregatedFields();
    Map<String, Set<String>> aggregatorToFields = fieldsAggregatable.getAggregatorToFields();
    Map<String, Map<String, String>> aggregatorToFieldToName = fieldsAggregatable.getAggregatorToFieldToName();

    List<Map<String, GPOMutable>> keys = dataResult.getKeys();
    List<Map<String, GPOMutable>> values = dataResult.getValues();

    for (int index = 0;
        index < keys.size();
        index++) {
      Map<String, GPOMutable> key = keys.get(index);
      Map<String, GPOMutable> value = values.get(index);

      JSONObject valueJO = new JSONObject();

      GPOMutable gpoKey = key.values().iterator().next();

      if (hasTime && nonAggregatedFields.getFields().contains(DimensionsDescriptor.DIMENSION_TIME)) {
        Object time = gpoKey.getField(DimensionsDescriptor.DIMENSION_TIME);
        valueJO.put(DimensionsDescriptor.DIMENSION_TIME, time);
      }

      for (String field : nonAggregatedFields.getFields()) {
        if (field.equals(DimensionsDescriptor.DIMENSION_TIME)) {
          //Do nothing
        } else if (gpoKey.getFieldDescriptor().getFields().getFields().contains(field)) {
          valueJO.put(field, resultFormatter.format(gpoKey.getField(field)));
        } else {
          valueJO.put(field, ALL);
        }
      }

      Map<String, Type> fieldNameToType = Maps.newHashMap();
      for (Map.Entry<String, GPOMutable> entry : value.entrySet()) {
        String aggregatorName = entry.getKey();
        GPOMutable aggregateValues = entry.getValue();
        Set<String> fields = aggregatorToFields.get(aggregatorName);

        fieldNameToType.clear();
        getFieldNameToTypeTo(aggregateValues, fieldNameToType);

        for (String field : fields) {
          String compoundName = aggregatorToFieldToName.get(aggregatorName).get(field);
          //valueJO.put(compoundName, resultFormatter.format(aggregateValues.getField(field)));
          valueJO.put(compoundName, formatValueField(resultFormatter, aggregateValues, fieldNameToType, field));
        }
      }

      data.put(valueJO);
    }

    if (!dataResult.getQuery().isOneTime()) {
      jo.put(DataResultDimensional.FIELD_COUNTDOWN,
          dataResult.getCountdown());
    }

    return jo.toString();
  }

  /*
   * convert typeToFields to field to type
   */
  protected void getFieldNameToTypeTo(GPOMutable values, Map<String, Type> fieldNameToType)
  {
    Map<Type, List<String>> typeToFields = values.getFieldDescriptor().getTypeToFields();
    for (Map.Entry<Type, List<String>> entry : typeToFields.entrySet()) {
      for (String fieldName : entry.getValue()) {
        fieldNameToType.put(fieldName, entry.getKey());
      }

    }
  }

  protected String formatValueField(ResultFormatter resultFormatter, GPOMutable aggregateValues,
      Map<String, Type> fieldNameToType, String fieldName)
  {
    Type type = fieldNameToType.get(fieldName);
    if (Type.OBJECT == type) {
      return getFormatterForObject(resultFormatter).format(aggregateValues.getFieldObject(fieldName));
    }

    return resultFormatter.format(aggregateValues.getField(fieldName));
  }

  /**
   * The formatter for format object.
   */
  protected ResultFormatter formatterForObject;

  /**
   * get the ResultFormatter which support format object.
   * Right now only support map.
   * @param preferFormatter
   * @return
   */
  protected ResultFormatter getFormatterForObject(ResultFormatter preferFormatter)
  {
    if (preferFormatter instanceof MapResultFormatter) {
      return preferFormatter;
    }

    if (formatterForObject == null) {
      formatterForObject = new MapResultFormatter(preferFormatter);
    }

    return formatterForObject;
  }

  private static final Logger LOG = LoggerFactory.getLogger(DataResultDimensionalSerializer.class);
}
