// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Modifies a {@link MergedAndroidData} manifest for the specified densities.
 */
public class DensitySpecificManifestProcessor {

  static final ImmutableList<String> SCREEN_SIZES = ImmutableList.of(
      "small", "normal", "large", "xlarge");
  static final ImmutableBiMap<String, String> SCREEN_DENSITIES =
      ImmutableBiMap.<String, String>builder()
      .put("ldpi", "ldpi")
      .put("mdpi", "mdpi")
      .put("tvdpi", "213")
      .put("hdpi", "hdpi")
      .put("280dpi", "280")
      .put("xhdpi", "xhdpi")
      .put("400dpi", "400")
      .put("420dpi", "420")
      .put("xxhdpi", "480")
      .put("560dpi", "560")
      .put("xxxhdpi", "640").build();

  private static final ImmutableMap<String, Boolean> SECURE_XML_FEATURES = ImmutableMap.of(
      XMLConstants.FEATURE_SECURE_PROCESSING, true,
      "http://xml.org/sax/features/external-general-entities", false,
      "http://xml.org/sax/features/external-parameter-entities", false,
      "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

  private static DocumentBuilder getSecureDocumentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(
        "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", null);
    factory.setValidating(false);
    factory.setXIncludeAware(false);
    for (Map.Entry<String, Boolean> featureAndValue : SECURE_XML_FEATURES.entrySet()) {
        try {
          factory.setFeature(featureAndValue.getKey(), featureAndValue.getValue());
        } catch (ParserConfigurationException e) {
          throw new FactoryConfigurationError(e,
              "Xerces DocumentBuilderFactory doesn't support the required security features: "
              + e.getMessage());
        }
    }
    return factory.newDocumentBuilder();
  }

  private final List<String> densities;
  private final Path out;

  /**
   * @param densities An array of string densities to use for filtering resources.
   * @param out The path to use for the generated manifest.
   */
  public DensitySpecificManifestProcessor(List<String> densities, Path out) {
    this.densities = densities;
    this.out = out;
  }

  /**
   * Modifies the manifest to contain a &lt;compatible-screens&gt; section corresponding to the
   * specified densities. If the manifest already contains a superset of the
   * &lt;compatible-screens&gt; section to be created, it is left unchanged.
   *
   * @throws ManifestProcessingException when the manifest cannot be properly modified.
   */
  public Path process(Path manifest) throws ManifestProcessingException {
    if (densities.isEmpty()) {
      return manifest;
    }
    try {
      DocumentBuilder db = getSecureDocumentBuilder();
      Document doc = db.parse(Files.newInputStream(manifest));

      NodeList manifestElements = doc.getElementsByTagName("manifest");
      if (manifestElements.getLength() != 1) {
        throw new ManifestProcessingException(
            String.format("Manifest %s does not contain exactly one <manifest> tag. "
                + "It contains %d.", manifest, manifestElements.getLength()));
      }
      Node manifestElement = manifestElements.item(0);

      Set<String> existingDensities = new HashSet<>();
      NodeList screenElements = doc.getElementsByTagName("screen");
      for (int i = 0; i < screenElements.getLength(); i++) {
        Node screen = screenElements.item(i);
        existingDensities.add(SCREEN_DENSITIES.inverse().get(
            screen.getAttributes().getNamedItem("android:screenDensity").getNodeValue()));
      }
      if (existingDensities.containsAll(densities)) {
        return manifest;
      }

      NodeList compatibleScreensElements = doc.getElementsByTagName("compatible-screens");
      for (int i = 0; i < compatibleScreensElements.getLength(); i++) {
        Node compatibleScreensElement = compatibleScreensElements.item(i);
        compatibleScreensElement.getParentNode().removeChild(compatibleScreensElement);
      }

      Node compatibleScreens = doc.createElement("compatible-screens");
      manifestElement.appendChild(compatibleScreens);

      for (String density : densities) {
        for (String screenSize : SCREEN_SIZES) {
          Element screen = doc.createElement("screen");
          screen.setAttribute("android:screenSize", screenSize);
          screen.setAttribute("android:screenDensity", SCREEN_DENSITIES.get(density));
          compatibleScreens.appendChild(screen);
        }
      }

      Files.createDirectories(out.getParent());
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.newTransformer().transform(
          new DOMSource(doc), new StreamResult(Files.newOutputStream(out)));
      return out;

    } catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
      throw new ManifestProcessingException(e.getMessage());
    }
  }
}
