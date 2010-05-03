/**
 * Copyright - See the COPYRIGHT that is included with this disctibution.
 * EPICS JavaIOC is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.ca.client;

/**
 * Access Rights.
 * @author mrk
 *
 */
public enum AccessRights {
    /**
     * Neither read or write access is allowed.
     */
    none,
    /**
     * Read access is allowed but write access is not allowed.
     */
    read,
    /**
     * Both read and write access are allowed.
     */
    readWrite
}
