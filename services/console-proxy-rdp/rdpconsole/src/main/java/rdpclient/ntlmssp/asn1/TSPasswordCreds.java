// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package rdpclient.ntlmssp.asn1;

import common.asn1.OctetString;
import common.asn1.Sequence;
import common.asn1.Tag;

/**
 * <pre>
 * TSPasswordCreds ::= SEQUENCE {
 *   domainName    [0] OCTET STRING,
 *   userName      [1] OCTET STRING,
 *   password      [2] OCTET STRING
 * }
 * </pre>
 * 
 * <ul>
 * <li>domainName: Contains the name of the user's account domain, as defined in
 * [MS-GLOS].
 * 
 * <li>userName: Contains the user's account name.
 * 
 * <li>Password: Contains the user's account password.
 * </ul>
 * 
 * @see http://msdn.microsoft.com/en-us/library/cc226783.aspx
 */
public class TSPasswordCreds extends Sequence {
    public OctetString domainName = new OctetString("domainName") {
        {
            explicit = true;
            tagClass = CONTEXT_CLASS;
            tagNumber = 0;
        }
    };
    public OctetString userName = new OctetString("userName") {
        {
            explicit = true;
            tagClass = CONTEXT_CLASS;
            tagNumber = 1;
        }
    };
    public OctetString password = new OctetString("password") {
        {
            explicit = true;
            tagClass = CONTEXT_CLASS;
            tagNumber = 2;
        }
    };

    public TSPasswordCreds(String name) {
        super(name);
        this.tags = new Tag[] {domainName, userName, password};
    }

    @Override
    public Tag deepCopy(String suffix) {
        return new TSPasswordCreds(name + suffix).copyFrom(this);
    }

}
