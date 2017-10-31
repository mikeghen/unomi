/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.plugins.baseplugin.actions;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdatePropertiesAction implements ActionExecutor {

    public static final String PROPS_TO_ADD = "propertiesToAdd";
    public static final String PROPS_TO_UPDATE = "propertiesToUpdate";
    public static final String PROPS_TO_DELETE = "propertiesToDelete";

    public static final String SYS_PROPS_TO_ADD = "systemPropertiesToAdd";
    public static final String SYS_PROPS_TO_UPDATE = "systemPropertiesToUpdate";
    public static final String SYS_PROPS_TO_DELETE = "systemPropertiesToDelete";

    public static final String TARGET_ID_KEY = "targetId";
    public static final String TARGET_TYPE_KEY = "targetType";

    public static final String TARGET_TYPE_PROFILE = "profile";

    Logger logger = LoggerFactory.getLogger(UpdatePropertiesAction.class.getName());

    private ProfileService profileService;
    private EventService eventService;

    public int execute(Action action, Event event) {

        Profile target = event.getProfile();

        String targetId = (String) event.getProperty(TARGET_ID_KEY);
        String targetType = (String) event.getProperty(TARGET_TYPE_KEY);
        
        if (StringUtils.isNotBlank(targetId) && event.getProfile() != null && !targetId.equals(event.getProfile().getItemId())) {
            target = TARGET_TYPE_PROFILE.equals(targetType)?profileService.load(targetId) : profileService.loadPersona(targetId);
            if (target == null) {
                logger.warn("No profile found with Id : {}. Update skipped.", targetId);
                return EventService.NO_CHANGE;
            }
        }

        boolean isProfileOrPersonaUpdated = false;

        Map<String, Object> propsToAdd = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_ADD);
        HashMap<String, Object> sysPropsToAdd = (HashMap<String, Object>) event.getProperties().get(SYS_PROPS_TO_ADD);

        if (propsToAdd != null) {
            if(sysPropsToAdd != null) {
                propsToAdd.putAll(sysPropsToAdd);
            }
            for (String prop : propsToAdd.keySet()) {
                PropertyType propType = null;
                if (prop.startsWith("properties.")) {
                    propType = profileService.getPropertyType(prop.substring("properties.".length()));
                }
                if (propType != null) {
                    isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, PropertyHelper.getValueByTypeId(propsToAdd.get(prop), propType.getValueTypeId()), "setIfMissing");
                } else {
                    isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, propsToAdd.get(prop), "setIfMissing");
                }
            }
        }

        Map<String, Object> propsToUpdate = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_UPDATE);
        Map<String, Object> sysPropsToUpdate = (HashMap<String, Object>) event.getProperties().get(SYS_PROPS_TO_UPDATE);
        if (propsToUpdate != null) {
            if (sysPropsToUpdate != null) {
                propsToUpdate.putAll(sysPropsToUpdate);
            }
            for (String prop : propsToUpdate.keySet()) {
                PropertyType propType = null;
                if (prop.startsWith("properties.")) {
                    propType = profileService.getPropertyType(prop.substring("properties.".length()));
                }
                if (propType != null) {
                    isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, PropertyHelper.getValueByTypeId(propsToUpdate.get(prop), propType.getValueTypeId()), "alwaysSet");
                } else {
                    isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, propsToUpdate.get(prop), "alwaysSet");
                }
            }
        }

        List<String> propsToDelete = (List<String>) event.getProperties().get(PROPS_TO_DELETE);
        List<String> sysPropsToDelete = (List<String>) event.getProperties().get(SYS_PROPS_TO_DELETE);
        if (propsToDelete != null) {
            if (sysPropsToDelete != null) {
                propsToDelete.addAll(sysPropsToDelete);
            }
            for (String prop : propsToDelete) {
                isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, null, "remove");
            }
        }

        if (StringUtils.isNotBlank(targetId) && isProfileOrPersonaUpdated &&
                event.getProfile() != null && !targetId.equals(event.getProfile().getItemId())) {
            if(TARGET_TYPE_PROFILE.equals(event.getProfile().getItemType())) {
                profileService.save(target);
                Event profileUpdated = new Event("profileUpdated", null, target, null, null, target, new Date());
                profileUpdated.setPersistent(false);
                int changes = eventService.send(profileUpdated);
                if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                    profileService.save(target);
                }
            } else {
                profileService.savePersona((Persona) target);
            }

            return EventService.NO_CHANGE;

        }

        return isProfileOrPersonaUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;

    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

}