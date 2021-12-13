package org.github.common;

/**
 * @author zengchzh
 * @date 2021/12/10
 */
public enum MsgType {

    REQ((byte) 0),
    RES((byte) 1),
    BEAT((byte) 3)
    ;

    private final byte code;


    MsgType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }


    public static MsgType get(byte code) {
        for (MsgType msgType : MsgType.values()) {
            if (code == msgType.getCode()) {
                return msgType;
            }
        }
        return null;
    }
}