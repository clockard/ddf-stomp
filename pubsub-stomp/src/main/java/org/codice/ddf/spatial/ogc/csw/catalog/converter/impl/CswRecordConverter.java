/**
 * Copyright (c) Codice Foundation
 * 
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 * 
 **/
package org.codice.ddf.spatial.ogc.csw.catalog.converter.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;

import net.opengis.cat.csw.v_2_0_2.ElementSetType;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.spatial.ogc.catalog.common.converter.XmlNode;
import org.codice.ddf.spatial.ogc.csw.catalog.common.BoundingBoxReader;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswRecordMetacardType;
import org.codice.ddf.spatial.ogc.csw.catalog.converter.RecordConverter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.thoughtworks.xstream.io.xml.WstxDriver;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;

import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.AttributeType.AttributeFormat;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.impl.AttributeDescriptorImpl;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.BasicTypes;
import ddf.catalog.data.impl.MetacardImpl;

/**
 * Converts CSW Record to a Metacard.
 * 
 * @author rodgersh
 * 
 */

public class CswRecordConverter implements RecordConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CswRecordConverter.class);

    private static final String UTF8_ENCODING = "UTF-8";
    
    private static final DatatypeFactory XSD_FACTORY;

    protected MetacardType metacardType;

    private String sourceId = null;

    protected HierarchicalStreamCopier copier = new HierarchicalStreamCopier();

    protected NoNameCoder noNameCoder = new NoNameCoder();
    
    private Map<String, String> metacardToCswAttributeMappings;
    
    Map<String, String> prefixToUriMapping;
    
    private String productRetrievalMethod;
    
    private List<QName> fieldsToWrite;
    
    private String resourceUriMapping;
    
    private String thumbnailMapping;
    
    private boolean  isLonLatOrder;

    /**
     * The map of metacard attributes that both the basic DDF MetacardTypeImpl and the CSW
     * MetacardType define as attributes. This is used to detect these element tags when
     * unmarshalling XML so that the tag name can be modified with a CSW-unique prefix before
     * attempting to lookup the attribute descriptor corresponding to the tag.
     */
    private static final List<String> CSW_OVERLAPPING_ATTRIBUTE_NAMES = Arrays.asList(
            Metacard.TITLE, Metacard.CREATED, Metacard.MODIFIED);

    /**
     * Mapping of the CSW attribute names to Metacard attribute names where overlap or customization
     * may occur.
     */
    protected Map<String, String> cswToMetacardAttributeNames;

    static {
        DatatypeFactory factory = null;
        try {
            factory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            LOGGER.error("Failed to create xsdFactory: {}", e.getMessage());
        }
        XSD_FACTORY = factory;
    }
    public CswRecordConverter(Map<String, String> metacardToCswAttributeMappings, String productRetrievalMethod, String resourceUriMapping, String thumbnailMapping, boolean isLonLatOrder) {
        this(metacardToCswAttributeMappings, null, productRetrievalMethod, resourceUriMapping, thumbnailMapping, isLonLatOrder);
    }

    public CswRecordConverter(Map<String, String> metacardToCswAttributeMappings, Map<String, String> prefixToUriMapping, String productRetrievalMethod, String resourceUriMapping, String thumbnailMapping, boolean isLonLatOrder) {
        this.metacardToCswAttributeMappings = metacardToCswAttributeMappings;
        this.prefixToUriMapping = prefixToUriMapping;
        this.productRetrievalMethod = productRetrievalMethod;
        this.resourceUriMapping = resourceUriMapping;
        this.thumbnailMapping = thumbnailMapping;
        this.isLonLatOrder = isLonLatOrder;
        setCswToMetacardAttributeMappings(metacardToCswAttributeMappings);
    }

    public void setCswToMetacardAttributeMappings(Map<String, String> metacardToAttribute) {
        cswToMetacardAttributeNames = new HashMap<String, String>();

        // Default mappings (never overridden by metatype.xml settings)
        cswToMetacardAttributeNames.put(CswRecordMetacardType.CSW_TITLE, Metacard.TITLE);

        // Mappings specified by metatype.xml - should always at least
        // contain the default mappings
        if (metacardToAttribute != null && metacardToAttribute.size() > 0) {
            for (String metacardAttrName : metacardToAttribute.keySet()) {
                String cswAttrName = metacardToAttribute.get(metacardAttrName);

                // Check if this mapping has overlaps with basic Metacard
                // attribute names -
                // if so, need to prepend CSW prefix to attribute name so
                // that it is uniquely named
                // (see CswRecordMetacardType class)
                if (CSW_OVERLAPPING_ATTRIBUTE_NAMES.contains(cswAttrName)) {
                    cswAttrName = CswRecordMetacardType.CSW_ATTRIBUTE_PREFIX + cswAttrName;
                }

                cswToMetacardAttributeNames.put(cswAttrName, metacardAttrName);
            }
        } else {
            LOGGER.info("No default attribute mappings provided by caller - using fallback mappings");
            loadFallbackMappings();
        }
    }

    public void setFieldsToWrite(List<QName> fieldsToWrite) {
        this.fieldsToWrite = fieldsToWrite;
    }

    public List<QName> getFieldsToWrite() {
        return fieldsToWrite;
    }


    private void loadFallbackMappings() {
        cswToMetacardAttributeNames = DefaultCswRecordMap.getDefaultCswRecordMap()
                .getCswToMetacardAttributeNames();
    }

    @Override
    public boolean canConvert(Class clazz) {
        return Metacard.class.isAssignableFrom(clazz);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        if (source == null || !(source instanceof Metacard)) {
            LOGGER.warn("Failed to marshal Metacard: {}", source);
            return;
        }
        Metacard metacard = (Metacard) source;

        if (fieldsToWrite != null) {
            for (QName qName : fieldsToWrite) {
                if (qName != null && !qName.equals(CswRecordMetacardType.OWS_BOUNDING_BOX_QNAME)) {
                    String attrName = DefaultCswRecordMap.getDefaultCswRecordMap().getDefaultMetacardFieldFor(qName);
                    AttributeDescriptor ad = metacard.getMetacardType().getAttributeDescriptor(attrName);
                    
                    if (ad == null) {
                        ad = new AttributeDescriptorImpl(attrName, false, false, false, false, BasicTypes.STRING_TYPE);
                    }
                    writeAttribute(writer, context, metacard, ad, qName);
                }
            }
        } else { // write all fields
            Set<AttributeDescriptor> attrDescs = metacard.getMetacardType().getAttributeDescriptors();
            for (AttributeDescriptor ad : attrDescs) {
                List<QName> qNames = DefaultCswRecordMap.getDefaultCswRecordMap().getCswFieldsFor(ad.getName());
                for (QName qName : qNames) {
                    writeAttribute(writer, context, metacard, ad, qName);
                }
            }
        }
        
        if ((fieldsToWrite == null || fieldsToWrite
                .contains(CswRecordMetacardType.CSW_TEMPORAL_QNAME))
                &&  metacard.getEffectiveDate() != null && metacard.getExpirationDate() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    ISODateTimeFormat.dateTime().print(
                            ((Date) metacard.getEffectiveDate()).getTime()))
                    .append(" to ")
                    .append(ISODateTimeFormat.dateTime().print(
                            ((Date) metacard.getExpirationDate()).getTime()));
            writeValue(writer, context, null, CswRecordMetacardType.CSW_TEMPORAL_QNAME, sb.toString());
        }
        if ((fieldsToWrite == null || fieldsToWrite
                .contains(CswRecordMetacardType.CSW_SOURCE_QNAME))
                && metacard.getSourceId() != null) {
            writeValue(writer, context, null, CswRecordMetacardType.CSW_SOURCE_QNAME, metacard.getSourceId());
        }
        if (fieldsToWrite == null || fieldsToWrite.contains(CswRecordMetacardType.OWS_BOUNDING_BOX_QNAME)) {
            writeBoundingBox(writer, context, metacard);
        }
    }

    private void writeBoundingBox(HierarchicalStreamWriter writer, MarshallingContext context,
            Metacard metacard) {
        Set<AttributeDescriptor> attrDescs = metacard.getMetacardType().getAttributeDescriptors();
        List<Geometry> geometries = new LinkedList<Geometry>();
        
        for (AttributeDescriptor ad : attrDescs) {
            if (ad.getType() != null && AttributeFormat.GEOMETRY.equals(ad.getType().getAttributeFormat())) {
                
                Attribute attr = metacard.getAttribute(ad.getName());
                if (attr != null) {
                    if (ad.isMultiValued()) {
                        for (Serializable value : attr.getValues()) {
                            geometries.add(XmlNode.readGeometry((String) value));
                        }
                    } else {
                        geometries.add(XmlNode.readGeometry((String) attr.getValue()));
                    }
                }
            }
        }

        Geometry allGeometry = new GeometryCollection(geometries.toArray(new Geometry[geometries
                .size()]), new GeometryFactory());
        Envelope bounds = allGeometry.getEnvelopeInternal();
        if (!bounds.isNull()) {
            String bbox = CswConstants.OWS_NAMESPACE_PREFIX + CswConstants.NAMESPACE_DELIMITER
                    + CswRecordMetacardType.OWS_BOUNDING_BOX;
            String lower = CswConstants.OWS_NAMESPACE_PREFIX + CswConstants.NAMESPACE_DELIMITER
                    + CswConstants.OWS_LOWER_CORNER;
            String upper = CswConstants.OWS_NAMESPACE_PREFIX + CswConstants.NAMESPACE_DELIMITER
                    + CswConstants.OWS_UPPER_CORNER;
            writer.startNode(bbox);
            writer.addAttribute(CswConstants.CRS, CswConstants.SRS_URL);
            writer.startNode(lower);
            writer.setValue(bounds.getMinX() + " " + bounds.getMinY());
            writer.endNode();
            writer.startNode(upper);            
            writer.setValue(bounds.getMaxX() + " " + bounds.getMaxY());
            writer.endNode();
            
            writer.endNode();
        }
    }

    private void writeAttribute(HierarchicalStreamWriter writer, MarshallingContext context,
            Metacard metacard, AttributeDescriptor attributeDescriptor, QName field) {
        if (attributeDescriptor != null) {
            Attribute attr = metacard.getAttribute(attributeDescriptor.getName());
            if (attr != null) {
                if (attributeDescriptor.isMultiValued()) {
                    for (Serializable value : attr.getValues()) {
                        writeValue(writer, context, attributeDescriptor, field, value);
                    }
                } else {
                    writeValue(writer, context, attributeDescriptor, field, attr.getValue());
                }
            } else if (CswRecordMetacardType.REQUIRED_FIELDS.contains(field)) {
                writeValue(writer, context, attributeDescriptor, field, "");
                
            }
        }
    }

    private void writeValue(HierarchicalStreamWriter writer, MarshallingContext context,
            AttributeDescriptor attributeDescriptor, QName field, Serializable value) {

        String xmlValue = null;

        AttributeFormat attrFormat = null;
        if (attributeDescriptor != null && attributeDescriptor.getType() != null) {
            attrFormat = attributeDescriptor.getType().getAttributeFormat();
        }
        if (attrFormat == null) {
            attrFormat = AttributeFormat.STRING;
        }
        String name = null;
        if (!StringUtils.isBlank(field.getNamespaceURI())) {
            if (!StringUtils.isBlank(field.getPrefix())) {
                name = field.getPrefix() + CswConstants.NAMESPACE_DELIMITER
                        + field.getLocalPart();
            } else {
                name = field.getLocalPart();
            }
        } else {
            name = field.getLocalPart();
        }
        switch (attrFormat) {
        case BINARY:
            xmlValue = Base64.encodeBase64String((byte[]) value);
            break;
        case DATE:
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime((Date) value);
            xmlValue = XSD_FACTORY.newXMLGregorianCalendar(cal).toXMLFormat();
            break;
        case OBJECT:
            break;
        case GEOMETRY:
        case XML:
        default:
            xmlValue = value.toString();
            break;
        }
        // Write the node if we were able to convert it.
        if (xmlValue != null) {
            writer.startNode(name);
            if (!StringUtils.isBlank(field.getNamespaceURI())) {
                if (!StringUtils.isBlank(field.getPrefix())) {
                    writeNamespace(writer, field);
                } else {
                    writer.addAttribute(XMLConstants.XMLNS_ATTRIBUTE, field.getNamespaceURI());                    
                }
            }

            writer.setValue(xmlValue);
            writer.endNode();
        }
    }

    private void writeNamespace(HierarchicalStreamWriter writer, QName field) {
        if (prefixToUriMapping == null
                || !prefixToUriMapping.containsKey(field.getPrefix())
                || !prefixToUriMapping.get(field.getPrefix()).equals(
                        field.getNamespaceURI())) {
            writer.addAttribute(XMLConstants.XMLNS_ATTRIBUTE
                    + CswConstants.NAMESPACE_DELIMITER + field.getPrefix(),
                    field.getNamespaceURI());
        }
    }
    
    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {

        // Always ensure using latest mappings from CSW source's configuration.
        // If they changed,
        // CswSource will have updated its configuration object from which we
        // pull the latest
        // mappings and convert to our csw-to-metacard mappings.
        // KCW - Pretty sure we should only do this when we create the converter - if the config
        // changes a new converter will get created.
        // setCswToMetacardAttributeMappings(metacardToCswAttributeMappings);

        Metacard metacard = createMetacardFromCswRecord(reader, metacardType);
        metacard.setSourceId(sourceId);

        return metacard;
    }

    public void setMetacardType(MetacardType metacardType) {
        this.metacardType = metacardType;
    }

    public MetacardType getMetacardType() {
        return this.metacardType;
    }

    protected HierarchicalStreamReader copyXml(HierarchicalStreamReader hreader, StringWriter writer) {
        copier.copy(hreader, new CompactWriter(writer, noNameCoder));

        StaxDriver driver = new WstxDriver();
        try {
            // NOTE: must specify encoding here, otherwise the platform default
            // encoding will be used
            // which will not always work, esp. with foreign languages (e.g.,
            // Dutch from the geomatics site)
            return driver.createReader(new ByteArrayInputStream(writer.toString().getBytes(
                    UTF8_ENCODING)));
        } catch (UnsupportedEncodingException e) {
            LOGGER.warn("Unable create reader with UTF-8 encoding, Exception {}", e);
            return driver.createReader(new ByteArrayInputStream(writer.toString().getBytes()));
        }
    }

    protected MetacardImpl createMetacardFromCswRecord(HierarchicalStreamReader hreader,
            MetacardType type) {

        StringWriter metadataWriter = new StringWriter();
        HierarchicalStreamReader reader = copyXml(hreader, metadataWriter);

        MetacardImpl mc = new MetacardImpl(type);
        Map<String, Attribute> attributes = new HashMap<String, Attribute>();

        while (reader.hasMoreChildren()) {
            reader.moveDown();

            String name = reader.getNodeName();
            LOGGER.debug("node name: {}.", name);

            // Some attribute names overlap with basic Metacard attribute names,
            // e.g., "title".
            // So if this is one of those attribute names, get the CSW
            // attribute for the name to be looked up.
            if (CSW_OVERLAPPING_ATTRIBUTE_NAMES.contains(name)) {
                name = CswRecordMetacardType.CSW_ATTRIBUTE_PREFIX + name;
            }

            LOGGER.debug("Processing node {}", name);
            AttributeDescriptor attributeDescriptor = type.getAttributeDescriptor(name);

            Serializable value = null;

            // If XML node name matched an attribute descriptor in the
            // metacardType AND
            // the XML node has a non-blank value OR this is geometry/spatial
            // data,
            // then convert the CSW Record's property value for this XML node to
            // the
            // corresponding metacard attribute's value
            if (attributeDescriptor != null
                    && (StringUtils.isNotBlank(reader.getValue()) || BasicTypes.GEO_TYPE
                            .equals(attributeDescriptor.getType()))) {
                value = convertRecordPropertyToMetacardAttribute(attributeDescriptor.getType()
                        .getAttributeFormat(), reader);
            }

            if (null != value) {
                if (attributeDescriptor.isMultiValued()) {
                    if (attributes.containsKey(name)) {
                        AttributeImpl attribute = (AttributeImpl) attributes.get(name);
                        attribute.addValue(value);
                    } else {
                        attributes.put(name, new AttributeImpl(name, value));
                    }
                } else {
                    attributes.put(name, new AttributeImpl(name, value));
                }

                if (attributeDescriptor.getType().equals(BasicTypes.GEO_TYPE)) {
                    mc.setLocation((String) value);
                }
            }

            reader.moveUp();
        }

        for (String attrName : attributes.keySet()) {
            Attribute attr = attributes.get(attrName);
            mc.setAttribute(attr);

            // If this CSW attribute also maps to a basic metacard attribute,
            // (e.g., title, modified date, etc.)
            // then populate the basic metacard attribute with this attribute's
            // value.
            if (cswToMetacardAttributeNames.containsKey(attrName)) {
                String metacardAttrName = cswToMetacardAttributeNames.get(attrName);
                AttributeFormat cswAttributeFormat = type.getAttributeDescriptor(attrName)
                        .getType().getAttributeFormat();
                AttributeDescriptor metacardAttributeDescriptor = type
                        .getAttributeDescriptor(metacardAttrName);
                AttributeFormat metacardAttrFormat = metacardAttributeDescriptor.getType()
                        .getAttributeFormat();
                LOGGER.debug("Setting overlapping Metacard attribute [{}] to value in "
                        + "CSW attribute [{}] that has value [{}] and format {}", metacardAttrName,
                        attrName, attr.getValue(), metacardAttrFormat);
                if (cswAttributeFormat.equals(metacardAttrFormat)) {
                    mc.setAttribute(metacardAttrName, attr.getValue());
                } else {
                    Serializable value = convertStringValueToMetacardValue(metacardAttrFormat, attr
                            .getValue().toString());
                    mc.setAttribute(metacardAttrName, value);
                }
            }
        }

        // Save entire CSW Record XML as the metacard's metadata string
        mc.setMetadata(metadataWriter.toString());

        // Set Metacard ID to the CSW Record's identifier
        // TODO: may need to sterilize the CSW Record identifier if it has
        // special chars that clash
        // with usage in a URL - empirical testing with various CSW sites will
        // determine this.
        mc.setId((String) mc.getAttribute(CswRecordMetacardType.CSW_IDENTIFIER).getValue());

        try {
            if (type instanceof CswRecordMetacardType) {
                URI namespaceUri = new URI(((CswRecordMetacardType) type).getNamespaceURI());
                mc.setTargetNamespace(namespaceUri);
            }
        } catch (URISyntaxException e) {
            LOGGER.info("Error setting target namespace uri on metacard, Exception {}", e);
        }

        Date genericDate = new Date();
        if (mc.getEffectiveDate() == null) {
            mc.setEffectiveDate(genericDate);
        }
        if (mc.getCreatedDate() == null) {
            mc.setCreatedDate(genericDate);
        }
        if (mc.getModifiedDate() == null) {
            LOGGER.debug("modified date was null, setting to current date");
            mc.setModifiedDate(genericDate);
        }

        if (CswConstants.WCS_PRODUCT_RETRIEVAL.equals(productRetrievalMethod)) {

            // Set resource URI for where metacard's product is, using the WCS protocol.
            // Source ID will aid in looking up correct WcsResourceReader (which vary per CSW
            // federated source),
            // and metacard ID will be used as the coverage ID
            String wcsUri = "wcs:" + mc.getId();
            LOGGER.debug("wcsUri = {}", wcsUri);
            try {
                mc.setResourceURI(new URI(wcsUri));
            } catch (URISyntaxException e1) {
                LOGGER.warn("Unable to set metacard resource URI to " + wcsUri, e1);
            }
        } else {
            // Determine the csw field mapped to the resource uri and set that value
            // on the Metacard.RESOURCE_URI attribute
            // Default is for <source> field to define URI for product to be downloaded
            Attribute resourceUriAttr = mc.getAttribute(resourceUriMapping);
            if (resourceUriAttr != null && resourceUriAttr.getValue() != null) {
                String source = (String) resourceUriAttr.getValue();
                try {
                    mc.setResourceURI(new URI(source));
                } catch (URISyntaxException e) {
                    LOGGER.info("Error setting resource URI on metacard: {}, Exception {}", source, e);
                }
            }
        }

        // determine the csw field mapped to the thumbnail and set that value on
        // the Metacard.THUMBNAIL
        // attribute
        Attribute thumbnailAttr = mc.getAttribute(thumbnailMapping);
        if (thumbnailAttr != null && thumbnailAttr.getValue() != null) {
            String thumbnail = (String) thumbnailAttr.getValue();
            URL url;
            InputStream is = null;
            try {
                url = new URL(thumbnail);
                is = url.openStream();
                mc.setThumbnail(IOUtils.toByteArray(url.openStream()));
            } catch (MalformedURLException e) {
                LOGGER.info("Error setting thumbnail data on metacard: {}, Exception {}", thumbnail, e);
            } catch (IOException e) {
                LOGGER.info("Error setting thumbnail data on metacard: {}, Exception {}", thumbnail, e);
            } finally {
                IOUtils.closeQuietly(is);
            }

        }
        return mc;
    }

    /**
     * Converts properties in CSW records that overlap with same name as a basic Metacard attribute,
     * e.g., title. This conversion method is needed mainly because CSW records express all dates as
     * strings, whereas MetacardImpl expresses them as java.util.Date types.
     * 
     * @param attributeFormat
     * @param value
     * @return
     */
    public Serializable convertStringValueToMetacardValue(
            AttributeFormat attributeFormat, String value) {
        LOGGER.debug("converting csw record overlapping property {}", value);
        Serializable ser = null;
        
        if (attributeFormat == null) {
            LOGGER.debug("AttributeFormat was null when converting {}", value);
            return ser;
        }

        switch (attributeFormat) {
        case BOOLEAN:
            ser = Boolean.valueOf(value);
            break;
        case DOUBLE:
            ser = Double.valueOf(value);
            break;
        case FLOAT:
            ser = Float.valueOf(value);
            break;
        case INTEGER:
            ser = Integer.valueOf(value);
            break;
        case LONG:
            ser = Long.valueOf(value);
            break;
        case SHORT:
            ser = Short.valueOf(value);
            break;
        case XML:
        case STRING:
            ser = value;
            break;
        case DATE:
            ser = convertToDate(value);
            break;
        default:
            break;
        }

        return ser;
    }

    private Date convertToDate(String value) {
        // Dates are strings and expected to be in ISO8601 format, YYYY-MM-DD'T'hh:mm:ss.sss,
        // per annotations in the CSW Record schema. At least the date portion must be present;
        // the time zone and time are optional.
        try {
            return ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(value).toDate();
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Failed to convert to date {} from ISO Format: {}", value, e);
        }

        // failed to convert iso format, attempt to convert from xsd:date or xsd:datetime format
        // this format is used by the NSG interoperability CITE tests
        try {
            return XSD_FACTORY.newXMLGregorianCalendar(value).toGregorianCalendar().getTime();
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Unable to convert date {} from XSD format {} ", value, e);
        }

        // try from java date serialization for the default locale
        try {
            return DateFormat.getDateInstance().parse(value);
        } catch (ParseException e) {
            LOGGER.debug("Unable to convert date {} from default locale format {} ", value, e);
        }
        
        // default to current date
        LOGGER.warn("Unable to convert {} to a date object, defaulting to current time", value);
        return new Date();
    }

    /**
     * Converts the CSW record property to the specified Metacard attribute format.
     * 
     * @param attributeFormat
     * @param reader
     * @return
     */
    protected Serializable convertRecordPropertyToMetacardAttribute(
            AttributeFormat attributeFormat, HierarchicalStreamReader reader) {
        LOGGER.debug("converting csw record property {}", reader.getValue());
        Serializable ser = null;
        switch (attributeFormat) {
        case BOOLEAN:
            ser = Boolean.valueOf(reader.getValue());
            break;
        case DOUBLE:
            ser = Double.valueOf(reader.getValue());
            break;
        case FLOAT:
            ser = Float.valueOf(reader.getValue());
            break;
        case INTEGER:
            ser = Integer.valueOf(reader.getValue());
            break;
        case LONG:
            ser = Long.valueOf(reader.getValue());
            break;
        case SHORT:
            ser = Short.valueOf(reader.getValue());
            break;
        case XML:
        case STRING:
            ser = reader.getValue();
            break;
        case DATE:
            ser = convertStringValueToMetacardValue(attributeFormat, reader.getValue());
            break;
        case GEOMETRY:
            // We pass in isLonLatOrder, so we can determine coord order (LAT/LON vs
            // LON/LAT).
            ser = new BoundingBoxReader(reader, isLonLatOrder).getWkt();
            LOGGER.debug("WKT = {}", (String) ser);
            break;
        case BINARY:
            try {
                ser = reader.getValue().getBytes(UTF8_ENCODING);
            } catch (UnsupportedEncodingException e) {
                LOGGER.warn("Error encoding the binary value into the metacard.", e);
            }
            break;
        default:
            break;
        }
        return ser;

    }

    public void setSourceId(final String sourceId) {
        this.sourceId = sourceId;
    }

    public String getRootElementName(String elementSetType) {
        String rootElementName = null;
        switch (ElementSetType.valueOf(elementSetType)) {
        case BRIEF:
            rootElementName = CswConstants.CSW_BRIEF_RECORD;
            break;
        case SUMMARY:
            rootElementName = CswConstants.CSW_SUMMARY_RECORD;
            break;
        case FULL:
        default:
            rootElementName = CswConstants.CSW_RECORD;
            break;
        }
        return rootElementName;
    }
}