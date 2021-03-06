/**
 * ﻿Copyright (C) 2007 - 2016 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * As an exception to the terms of the GPL, you may copy, modify,
 * propagate, and distribute a work formed by combining 52°North WPS
 * GeoTools Modules with the Eclipse Libraries, or a work derivative of
 * such a combination, even if such copying, modification, propagation, or
 * distribution would otherwise violate the terms of the GPL. Nothing in
 * this exception exempts you from complying with the GPL in all respects
 * for all of the code used other than the Eclipse Libraries. You may
 * include this exception and its grant of permissions when you distribute
 * 52°North WPS GeoTools Modules. Inclusion of this notice with such a
 * distribution constitutes a grant of such permissions. If you do not wish
 * to grant these permissions, remove this paragraph from your
 * distribution. "52°North WPS GeoTools Modules" means the 52°North WPS
 * modules using GeoTools functionality - software licensed under version 2
 * or any later version of the GPL, or a work based on such software and
 * licensed under the GPL. "Eclipse Libraries" means Eclipse Modeling
 * Framework Project and XML Schema Definition software distributed by the
 * Eclipse Foundation and licensed under the Eclipse Public License Version
 * 1.0 ("EPL"), or a work based on such software and licensed under the EPL.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.wps.io.datahandler.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.GeometryAttributeImpl;
import org.geotools.feature.type.GeometryDescriptorImpl;
import org.geotools.feature.type.GeometryTypeImpl;
import org.geotools.filter.identity.GmlObjectIdImpl;
import org.geotools.gml3.ApplicationSchemaConfiguration;
import org.geotools.gml3.v3_2.GMLConfiguration;
import org.geotools.xml.Configuration;
import org.geotools.xml.Parser;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.io.SchemaRepository;
import org.n52.wps.io.data.binding.complex.GTVectorDataBinding;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.filter.identity.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.vividsolutions.jts.geom.Geometry;

/**
 * This parser handles xml files for GML 3.2.1
 *  
 * @author matthes rieke
 */
public class GML32BasicParser extends AbstractParser {
	
	private static Logger LOGGER = LoggerFactory.getLogger(GML32BasicParser.class);
	private Configuration configuration;
	private boolean setParserNonStrict;
	private boolean setBasicGMLConfiguration;

	

	public GML32BasicParser() {
		super();
		supportedIDataTypes.add(GTVectorDataBinding.class);
		
		org.n52.wps.PropertyDocument.Property[] properties = WPSConfig.getInstance().getPropertiesForParserClass(this.getClass().getCanonicalName());
		
		for (org.n52.wps.PropertyDocument.Property property : properties) {
                    if(property.getName().equals("setParserNonStrict")){
                        setParserNonStrict = Boolean.parseBoolean(property.getStringValue());
                    }else if(property.getName().equals("setBasicGMLConfiguration")){
                        setBasicGMLConfiguration = Boolean.parseBoolean(property.getStringValue());
                    }
                    
                }
		
	}
	
	public void setConfiguration(Configuration config) {
		this.configuration = config;
	}

	@Override
	public GTVectorDataBinding parse(InputStream stream, String mimeType, String schema) {

		FileOutputStream fos = null;
		try {
			File tempFile = File.createTempFile("wps", "tmp");
			finalizeFiles.add(tempFile); // mark for final delete
			fos = new FileOutputStream(tempFile);
			int i = stream.read();
			while (i != -1) {
				fos.write(i);
				i = stream.read();
			}
			fos.flush();
			fos.close();

			QName schematypeTuple = determineFeatureTypeSchema(tempFile);
			return parse(new FileInputStream(tempFile), schematypeTuple);
		}
		catch (IOException e) {
			if (fos != null) try { fos.close(); } catch (Exception e1) { }
			throw new IllegalArgumentException("Error while creating tempFile", e);
		}
	}

	public GTVectorDataBinding parse(InputStream input, QName schematypeTuple) {
	    
	    if(!setBasicGMLConfiguration){
	    
		if (configuration == null) {
			configuration = resolveConfiguration(schematypeTuple);
		}
	    }else{
	        configuration = new GMLConfiguration();
	    }
		Parser parser = new Parser(configuration);
		
		parser.setStrict(!setParserNonStrict);

		//parse
		FeatureCollection<?, SimpleFeature> fc = resolveFeatureCollection(parser, input);

		GTVectorDataBinding data = new GTVectorDataBinding(fc);

		return data;
	}
	

	private FeatureCollection<?, SimpleFeature> resolveFeatureCollection(Parser parser, InputStream input) {
		FeatureCollection<?, SimpleFeature> fc = null;
		List<SimpleFeature> simpleFeatureList = new ArrayList<>();
		try {
			Object parsedData = parser.parse(input);
			if (parsedData instanceof FeatureCollection){
				fc = (FeatureCollection<?, SimpleFeature>) parsedData;
			} else {
			    
			    SimpleFeatureType featureType = null;
				Object memberObject = ((HashMap<?, ?>) parsedData).get("member");
				
				if(memberObject instanceof List<?>){
	                            simpleFeatureList = ((ArrayList<SimpleFeature>)((HashMap<?, ?>) parsedData).get("member"));
	                            featureType = simpleFeatureList.get(0).getFeatureType();
				}else if(memberObject instanceof SimpleFeature){
				    SimpleFeature simpleFeature = (SimpleFeature)((HashMap<?, ?>) parsedData).get("member");
				    simpleFeatureList.add(simpleFeature);
				    featureType = simpleFeature.getFeatureType();
				}
				fc = new ListFeatureCollection(featureType, simpleFeatureList);
			}

			FeatureIterator<SimpleFeature> featureIterator = fc.features();
			while (featureIterator.hasNext()) {
				SimpleFeature feature = (SimpleFeature) featureIterator.next();
				
				if (feature.getDefaultGeometry() == null) {
					Collection<Property> properties = feature.getProperties();
					for (Property property : properties){
						try {
							Geometry g = (Geometry) property.getValue();
							if (g != null) {
								GeometryAttribute oldGeometryDescriptor = feature.getDefaultGeometryProperty();
								GeometryType type = new GeometryTypeImpl(property.getName(), (Class<?>) oldGeometryDescriptor.getType().getBinding(),
										oldGeometryDescriptor.getType().getCoordinateReferenceSystem(),
										oldGeometryDescriptor.getType().isIdentified(),
										oldGeometryDescriptor.getType().isAbstract(),
										oldGeometryDescriptor.getType().getRestrictions(),
										oldGeometryDescriptor.getType().getSuper()
										,oldGeometryDescriptor.getType().getDescription());

								GeometryDescriptor newGeometryDescriptor = new GeometryDescriptorImpl(type, property.getName(), 0, 1, true, null);
								Identifier identifier = new GmlObjectIdImpl(feature.getID());
								GeometryAttributeImpl geo = new GeometryAttributeImpl((Object) g, newGeometryDescriptor, identifier);
								feature.setDefaultGeometryProperty(geo);
								feature.setDefaultGeometry(g);

							}
						} catch (ClassCastException e){
							//do nothing
						}

					}
				}
			}
		} catch (IOException e) {
			LOGGER.warn(e.getMessage(), e);
			throw new RuntimeException(e);
		} catch (SAXException e) {
			LOGGER.warn(e.getMessage(), e);
			throw new RuntimeException(e);
		} catch (ParserConfigurationException e) {
			LOGGER.warn(e.getMessage(), e);
			throw new RuntimeException(e);
		}
		
		return fc;
	}
	

	private Configuration resolveConfiguration(QName schematypeTuple) {
		/*
		 * TODO all if-statements are nonsense.. clean up
		 */
		Configuration configuration = null;
		if (schematypeTuple != null) {
			String schemaLocation =  schematypeTuple.getLocalPart();
			if (schemaLocation.startsWith("http://schemas.opengis.net/gml/3.2")){
				configuration = new GMLConfiguration();
			} else {
				if (schemaLocation != null && schematypeTuple.getNamespaceURI()!=null){
					SchemaRepository.registerSchemaLocation(schematypeTuple.getNamespaceURI(), schemaLocation);
					configuration =  new ApplicationSchemaConfiguration(schematypeTuple.getNamespaceURI(), schemaLocation);
				} else {
					configuration = new GMLConfiguration();
				}
			}
		} else{
			configuration = new GMLConfiguration();
		}
		
		return configuration;
	}

	private QName determineFeatureTypeSchema(File file) {
		try {
			GML2Handler handler = new GML2Handler();
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);

			factory.newSAXParser().parse(new FileInputStream(file), handler); 

			String schemaUrl = handler.getSchemaUrl(); 

			if(schemaUrl == null){
				return null;
			}

			String namespaceURI = handler.getNameSpaceURI();

			/*
			 * TODO dude, wtf? Massive abuse of QName.
			 */
			return new QName(namespaceURI, schemaUrl);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} catch (SAXException e) {
			throw new IllegalArgumentException(e);
		} catch(ParserConfigurationException e) {
			throw new IllegalArgumentException(e);
		}
	}

	
	public static GML32BasicParser getInstanceForConfiguration(
			Configuration config) {
		GML32BasicParser parser = new GML32BasicParser();
		parser.setConfiguration(config);
		return parser;
	}



}

