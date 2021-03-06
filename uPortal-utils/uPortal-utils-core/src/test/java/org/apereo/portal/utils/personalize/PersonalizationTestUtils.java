/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.utils.personalize;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

public class PersonalizationTestUtils {
    public static IPerson mockPerson(String username) {
        IPerson person = Mockito.mock(IPerson.class);
        Map<String, List<Object>> atts = new HashMap<>();
        atts.put("username", Arrays.asList(username));
        Mockito.when(person.getAttributeMap()).thenReturn(atts);
        Mockito.when(person.getAttribute("username")).thenReturn(username);
        return person;
    }

    public static IPersonalizer mockPersonalizer() {
        final IPersonalizer p = new PersonalizerImpl();
        ReflectionTestUtils.setField(p, "prefix", "apereo.");
        ReflectionTestUtils.setField(p, "patternStr", "\\{\\{(.*?)\\}\\}");
        ReflectionTestUtils.invokeMethod(p, "postConstruct", null);
        return p;
    }

    public static IPersonManager mockPersonManager(HttpServletRequest req, IPerson person) {
        IPersonManager pMgr = Mockito.mock(IPersonManager.class);
        Mockito.when(pMgr.getPerson(req)).thenReturn(person);
        return pMgr;
    }
}
