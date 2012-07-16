/**
 *  Catroid: An on-device graphical programming language for Android devices
 *  Copyright (C) 2010  Catroid development team 
 *  (<http://code.google.com/p/catroid/wiki/Credits>)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package at.tugraz.ist.catroid.xml;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.util.Log;
import at.tugraz.ist.catroid.content.Script;
import at.tugraz.ist.catroid.content.Sprite;
import at.tugraz.ist.catroid.content.bricks.Brick;
import at.tugraz.ist.catroid.content.bricks.LoopBeginBrick;
import at.tugraz.ist.catroid.content.bricks.LoopEndBrick;

public class BrickParser {

	References references;
	ObjectCreator objectGetter = new ObjectCreator();
	CostumeParser costumeParser;
	XPathFactory xpathFactory = XPathFactory.newInstance();
	XPath xpath = xpathFactory.newXPath();

	public void parseBricks(Sprite foundSprite, Script foundScript, Element scriptElement, Node brickListNode,
			Map<String, Object> referencedObjects, List<ForwardReferences> forwardRefs)
			throws XPathExpressionException, InstantiationException, IllegalAccessException, InvocationTargetException,
			NoSuchMethodException, ClassNotFoundException, ParseException, SecurityException, NoSuchFieldException {
		NodeList brickNodes = brickListNode.getChildNodes();

		for (int k = 0; k < brickNodes.getLength(); k++) {
			Node currentBrickNode = brickNodes.item(k);
			if (currentBrickNode.getNodeType() != Node.TEXT_NODE) {
				Brick foundBrickObj = null;
				Element brickElement = (Element) currentBrickNode;
				String brickName = currentBrickNode.getNodeName();
				String brickReferenceAttr = References.getReferenceAttribute(brickElement);
				if (brickReferenceAttr != null) {
					//NodeList loopEndBricksInThisScript = scriptElement.getElementsByTagName("Bricks.LoopEndBrick");
					String refQuery = brickReferenceAttr.substring(brickReferenceAttr.lastIndexOf("Bricks"));
					if (brickName.equals("Bricks.LoopEndBrick") && (referencedObjects.containsKey(refQuery))) {
						foundBrickObj = (Brick) referencedObjects.get(refQuery);
						referencedObjects.remove(refQuery);

					} else {
						references = new References();
						foundBrickObj = (Brick) references.resolveReference(foundBrickObj, brickElement,
								brickReferenceAttr, referencedObjects, forwardRefs);
					}
				} else {

					NodeList brickValueNodes = brickElement.getChildNodes();
					foundBrickObj = getBrickObject(brickName, foundSprite, brickValueNodes, brickElement,
							referencedObjects, forwardRefs);
				}
				if (foundBrickObj != null) {
					String brickXPath = ParserUtil.getElementXpath(brickElement);
					referencedObjects.put(brickXPath, foundBrickObj);
					foundScript.addBrick(foundBrickObj);
				} else {
					throw new ParseException("Brick parsing incomplete");
				}
			}

		}
	}

	@SuppressWarnings("rawtypes")
	private Brick getBrickObject(String brickName, Sprite foundSprite, NodeList valueNodes, Element brickElement,
			Map<String, Object> referencedObjects, List<ForwardReferences> forwardRefs)
			throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException, ClassNotFoundException, XPathExpressionException,
			ParseException {

		String brickImplName = brickName.substring(7);
		Brick brickObject = null;
		Class brickClass = Class.forName("at.tugraz.ist.catroid.content.bricks." + brickImplName);
		Map<String, Field> brickFieldsToSet = objectGetter.getFieldMap(brickClass);
		brickObject = (Brick) objectGetter.getobjectOfClass(brickClass, "0");
		if (valueNodes != null) {
			parseBrickValues(foundSprite, valueNodes, brickObject, brickFieldsToSet, referencedObjects, forwardRefs);
		}
		String xp = ParserUtil.getElementXpath(brickElement);
		referencedObjects.put(xp, brickObject);
		return brickObject;
	}

	private void parseBrickValues(Sprite foundSprite, NodeList valueNodes, Brick brickObject,
			Map<String, Field> brickFieldsToSet, Map<String, Object> referencedObjects,
			List<ForwardReferences> forwardRefs) throws IllegalAccessException, XPathExpressionException,
			InstantiationException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException,
			ParseException {
		for (int l = 0; l < valueNodes.getLength(); l++) {
			Node brickValue = valueNodes.item(l);

			if (brickValue.getNodeType() == Node.TEXT_NODE) {
				continue;
			}
			String brickvalueName = brickValue.getNodeName();

			Field valueField = brickFieldsToSet.get(brickvalueName);
			if (valueField != null) {
				valueField.setAccessible(true);

				if (brickvalueName.equals("sprite")) {
					valueField.set(brickObject, foundSprite);
					continue;
				}
				String referenceAttribute = References.getReferenceAttribute(brickValue);
				if (referenceAttribute != null) {
					if (brickvalueName.equals("costumeData")) {
						costumeParser = new CostumeParser();
						costumeParser.setCostumedataOfBrick(brickObject, valueField, referenceAttribute,
								referencedObjects);
						continue;

					}

					XPathExpression exp = xpath.compile(referenceAttribute);
					Log.i("get brick obj", "xpath evaluated :" + referenceAttribute);
					Element referencedElement = (Element) exp.evaluate(brickValue, XPathConstants.NODE);
					String xp = ParserUtil.getElementXpath(referencedElement);
					Object valueObject = referencedObjects.get(xp);

					if (valueObject != null) {
						valueField.set(brickObject, valueObject);
					} else {
						ForwardReferences forwardRef = new ForwardReferences(brickObject, xp, valueField);
						forwardRefs.add(forwardRef);
					}
					continue;
				}

				if (brickValue.getChildNodes().getLength() > 1) {
					if (brickvalueName.endsWith("Brick")) {

						if (brickvalueName.equals("loopEndBrick")) {
							Element brickValueElement = (Element) brickValue;
							Element brickLoopBeginElement = (Element) brickValueElement.getElementsByTagName(
									"loopBeginBrick").item(0);
							String loopBeginRef = References.getReferenceAttribute(brickLoopBeginElement);
							if (loopBeginRef.equals("../..")) {
								LoopEndBrick foundLoopEndBrick = new LoopEndBrick(foundSprite,
										(LoopBeginBrick) brickObject);
								valueField.set(brickObject, foundLoopEndBrick);
								String childBrickXPath = ParserUtil.getElementXpath((Element) brickValue);
								String key = childBrickXPath.substring(childBrickXPath.lastIndexOf("Bricks"));
								referencedObjects.put(key, foundLoopEndBrick);
								continue;
							}
						}

						Character d = (brickvalueName.toUpperCase().charAt(0));
						brickvalueName = d.toString().concat(brickvalueName.substring(1));
						String prefix = "Bricks.";
						brickvalueName = prefix.concat(brickvalueName);
						Brick valueBrick = getBrickObject(brickvalueName, foundSprite, brickValue.getChildNodes(),
								(Element) brickValue, referencedObjects, forwardRefs);
						valueField.set(brickObject, valueBrick);
						String childBrickXPath = ParserUtil.getElementXpath((Element) brickValue);
						referencedObjects.put(childBrickXPath, valueBrick);
					} else {

						Map<String, Field> fieldMap = objectGetter.getFieldMap(valueField.getType());
						Object valueObj = objectGetter.getobjectOfClass(valueField.getType(), "0");
						getValueObject(valueObj, brickValue, fieldMap, referencedObjects, forwardRefs);

						valueField.set(brickObject, valueObj);

						String valueObjXPath = ParserUtil.getElementXpath((Element) brickValue);
						if (brickvalueName.equals("soundInfo")) {
							String suffix = valueObjXPath.substring(valueObjXPath.lastIndexOf("scriptList"));
							referencedObjects.put(suffix, valueObj);
						} else {
							referencedObjects.put(valueObjXPath, valueObj);
						}
					}
				} else {

					Node valueNode = brickValue.getChildNodes().item(0);
					if (valueNode != null) {
						String valueOfValue = valueNode.getNodeValue();
						Object valueObject = objectGetter.getobjectOfClass(valueField.getType(), valueOfValue);
						valueField.set(brickObject, valueObject);

					}
				}

			} else {
				throw new ParseException("Error when parsing values, no field in brick with the value name");
			}

		}
	}

	public void getValueObject(Object nodeObj, Node node, Map<String, Field> nodeClassFieldsToSet,
			Map<String, Object> referencedObjects, List<ForwardReferences> forwardRefs)
			throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException, XPathExpressionException {
		NodeList children = node.getChildNodes();
		if (children.getLength() > 1) {
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() != Node.TEXT_NODE) {
					String childNodeName = child.getNodeName();
					Field fieldWithNodeName = nodeClassFieldsToSet.get(childNodeName);
					if (fieldWithNodeName != null) {
						fieldWithNodeName.setAccessible(true);
						String refattr = References.getReferenceAttribute(child);
						if (refattr != null) {
							XPathExpression exp = xpath.compile(refattr);
							Log.i("parsing get value object method", "xpath evaluated");
							Element refList = (Element) exp.evaluate(child, XPathConstants.NODE);
							String xp = ParserUtil.getElementXpath(refList);
							Object valueObject = referencedObjects.get(xp);
							if (valueObject == null) {
								ForwardReferences forwardRef = new ForwardReferences(nodeObj, xp, fieldWithNodeName);
								forwardRefs.add(forwardRef);
							} else {
								fieldWithNodeName.set(nodeObj, valueObject);
							}
							continue;
						}
						if (child.getChildNodes().getLength() > 1) {
							Map<String, Field> fieldMap = objectGetter.getFieldMap(fieldWithNodeName.getType());
							Object valueObj = objectGetter.getobjectOfClass(fieldWithNodeName.getType(), "0");
							getValueObject(valueObj, child, fieldMap, referencedObjects, forwardRefs);
							String childXPath = ParserUtil.getElementXpath((Element) node);
							referencedObjects.put(childXPath, valueObj);
						} else {
							Node valueChildValue = child.getChildNodes().item(0);
							if (valueChildValue != null) {
								String valStr = valueChildValue.getNodeValue();
								Object valobj = objectGetter.getobjectOfClass(fieldWithNodeName.getType(), valStr);
								fieldWithNodeName.set(nodeObj, valobj);
							}
						}

					}

				}
			}
		}

	}

}