/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.failurereports.util;

import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.File;
import java.io.IOException;
import javax.xml.stream.XMLInputFactory;

public final class XmlResources {
    private static final ObjectMapper XML_MAPPER = createXmlMapper();

    public static <T> void writeXml(File xmlFile, T testSuite) throws IOException {
        XML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(xmlFile, testSuite);
    }

    public static <T> T readXml(File xmlFile, Class<T> cls) throws IOException {
        return XML_MAPPER.readValue(xmlFile, cls);
    }

    public static <T> T readXml(String xmlString, Class<T> cls) throws JsonProcessingException {
        return XML_MAPPER.readValue(xmlString, cls);
    }

    private static ObjectMapper createXmlMapper() {
        XMLInputFactory input = new WstxInputFactory();
        input.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
        input.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        JacksonXmlModule xmlModule = new JacksonXmlModule();
        xmlModule.setDefaultUseWrapper(false);
        ObjectMapper objectMapper = new XmlMapper(
                XmlFactory.builder()
                        .xmlInputFactory(input)
                        .xmlOutputFactory(new WstxOutputFactory())
                        .build(),
                xmlModule);
        return objectMapper;
    }

    private XmlResources() {}
}
