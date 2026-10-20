//
// Created by admin on 17.08.2023.
//

#include "editobject.h"
#include <jni.h>
#include "main.h"

#include "../game/game.h"
#include "net/netgame.h"
#include "gui/gui.h"
#include "jniutil.h"

extern CJavaWrapper *pJavaWrapper;
extern UI *pUI;
extern CNetGame *pNetGame;

static void resetObjectEditorState() {
    CObjectEditor::iEditedId = INVALID_EDITED_SLOT;
    CObjectEditor::editType = CObjectEditor::TYPE_NONE;
    CObjectEditor::bIsToggle = false;
    CObjectEditor::hasOriginalState = false;
    CObjectEditor::time = 0;
}

void CObjectEditor::startEditPlayerAttach(int slot)
{
    if (!pNetGame || !pNetGame->GetPlayerPool() || !pNetGame->GetPlayerPool()->GetLocalPlayer()) {
        return;
    }
    auto attach = pNetGame->GetPlayerPool()->GetLocalPlayer()->GetPlayerPed()->GetAttachedObject(slot);
    if (!attach) {
        if (pUI) pUI->chat()->addDebugMessage("Invalid attach slot %d", slot);
        return;
    }

    CObjectEditor::iEditedId = slot;
    CObjectEditor::editType = TYPE_PLAYER_ATTACH;
    CObjectEditor::time = GetTickCount();
    CObjectEditor::originalModelId = attach->dwModelId;
    CObjectEditor::originalBone = attach->dwSampBone;
    CObjectEditor::originalTransform[0] = attach->vecOffset.x;
    CObjectEditor::originalTransform[1] = attach->vecOffset.y;
    CObjectEditor::originalTransform[2] = attach->vecOffset.z;
    CObjectEditor::originalTransform[3] = attach->vecRotation.x;
    CObjectEditor::originalTransform[4] = attach->vecRotation.y;
    CObjectEditor::originalTransform[5] = attach->vecRotation.z;
    CObjectEditor::originalTransform[6] = attach->vecScale.x;
    CObjectEditor::originalTransform[7] = attach->vecScale.y;
    CObjectEditor::originalTransform[8] = attach->vecScale.z;
    CObjectEditor::hasOriginalState = true;

    CObjectEditor::showGui();
}

void CObjectEditor::startEditObject(uint16_t objectId)
{
    if (!pNetGame || !pNetGame->GetObjectPool()) {
        return;
    }
    auto pObject = pNetGame->GetObjectPool()->GetAt(objectId);
    if (!pObject || !pObject->m_pEntity) {
        if (pUI) pUI->chat()->addDebugMessage("Invalid object id %d", objectId);
        return;
    }

    CObjectEditor::iEditedId = objectId;
    CObjectEditor::editType = TYPE_OBJECT;
    CObjectEditor::time = GetTickCount();

    RwMatrix matrix = pObject->m_pEntity->GetMatrix().ToRwMatrix();
    CVector rotation;
    pObject->GetRotation(&rotation.x, &rotation.y, &rotation.z);
    CObjectEditor::originalTransform[0] = matrix.pos.x;
    CObjectEditor::originalTransform[1] = matrix.pos.y;
    CObjectEditor::originalTransform[2] = matrix.pos.z;
    CObjectEditor::originalTransform[3] = rotation.x;
    CObjectEditor::originalTransform[4] = rotation.y;
    CObjectEditor::originalTransform[5] = rotation.z;
    CObjectEditor::originalTransform[6] = 1.0f;
    CObjectEditor::originalTransform[7] = 1.0f;
    CObjectEditor::originalTransform[8] = 1.0f;
    CObjectEditor::hasOriginalState = true;

    CObjectEditor::showGui();
}

void CObjectEditor::showGui() {
    pJavaWrapper->ShowEditObject();

    bIsToggle = true;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_samp_mobile_game_ui_AttachEdit_Exit(JNIEnv *env, jobject thiz) {
    const int slot = CObjectEditor::iEditedId;
    const CObjectEditor::eEditType type = CObjectEditor::editType;

    if (pNetGame && slot != INVALID_EDITED_SLOT && CObjectEditor::hasOriginalState) {
        if (type == CObjectEditor::TYPE_PLAYER_ATTACH
                && pNetGame->GetPlayerPool()
                && pNetGame->GetPlayerPool()->GetLocalPlayer()) {
            CPlayerPed* player = pNetGame->GetPlayerPool()->GetLocalPlayer()->GetPlayerPed();
            auto attach = player ? player->GetAttachedObject(slot) : nullptr;
            if (attach) {
                CVector offset(CObjectEditor::originalTransform[0],
                               CObjectEditor::originalTransform[1],
                               CObjectEditor::originalTransform[2]);
                CVector rotation(CObjectEditor::originalTransform[3],
                                 CObjectEditor::originalTransform[4],
                                 CObjectEditor::originalTransform[5]);
                CVector scale(CObjectEditor::originalTransform[6],
                              CObjectEditor::originalTransform[7],
                              CObjectEditor::originalTransform[8]);
                attach->vecOffset = offset;
                attach->vecRotation = rotation;
                attach->vecScale = scale;
                CObjectEditor::SendOnEditAttach(
                        CObjectEditor::EDIT_RESPONSE_CANCEL,
                        slot,
                        CObjectEditor::originalModelId,
                        CObjectEditor::originalBone,
                        offset,
                        rotation,
                        scale);
            }
        } else if (type == CObjectEditor::TYPE_OBJECT && pNetGame->GetObjectPool()) {
            auto pObject = pNetGame->GetObjectPool()->GetAt(static_cast<uint16_t>(slot));
            if (pObject && pObject->m_pEntity) {
                const float* original = CObjectEditor::originalTransform;
                pObject->m_pEntity->SetPosn(original[0], original[1], original[2]);
                pObject->m_pEntity->UpdateRW();
                pObject->m_pEntity->UpdateRwFrame();
                pObject->InstantRotate(original[3], original[4], original[5]);

                RakNet::BitStream bsSend;
                bsSend.Write((bool) CObjectEditor::bPlayerObj);
                bsSend.Write((uint16_t) slot);
                bsSend.Write((uint32_t) CObjectEditor::EDIT_RESPONSE_CANCEL);
                bsSend.Write((float) original[0]);
                bsSend.Write((float) original[1]);
                bsSend.Write((float) original[2]);
                bsSend.Write((float) original[3]);
                bsSend.Write((float) original[4]);
                bsSend.Write((float) original[5]);
                pNetGame->GetRakClient()->RPC(&RPC_EditObject, &bsSend, HIGH_PRIORITY, RELIABLE,
                                              0, false, UNASSIGNED_NETWORK_ID, nullptr);
            }
        }
    }

    resetObjectEditorState();
    if (pJavaWrapper) pJavaWrapper->HideEditObject();
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_samp_mobile_game_ui_AttachEdit_ReadValues(JNIEnv *env, jobject thiz) {
    if (!pNetGame || CObjectEditor::iEditedId == INVALID_EDITED_SLOT
            || !CObjectEditor::hasOriginalState) {
        return nullptr;
    }

    jfloat values[10] = {};
    values[0] = static_cast<jfloat>(CObjectEditor::editType);
    const int slot = CObjectEditor::iEditedId;
    if (CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {
        if (!pNetGame->GetPlayerPool() || !pNetGame->GetPlayerPool()->GetLocalPlayer()) {
            return nullptr;
        }
        CPlayerPed* player = pNetGame->GetPlayerPool()->GetLocalPlayer()->GetPlayerPed();
        auto attach = player ? player->GetAttachedObject(slot) : nullptr;
        if (!attach) return nullptr;
        values[1] = attach->vecOffset.x;
        values[2] = attach->vecOffset.y;
        values[3] = attach->vecOffset.z;
        values[4] = attach->vecRotation.x;
        values[5] = attach->vecRotation.y;
        values[6] = attach->vecRotation.z;
        values[7] = attach->vecScale.x;
        values[8] = attach->vecScale.y;
        values[9] = attach->vecScale.z;
    } else if (CObjectEditor::editType == CObjectEditor::TYPE_OBJECT) {
        if (!pNetGame->GetObjectPool()) return nullptr;
        auto pObject = pNetGame->GetObjectPool()->GetAt(static_cast<uint16_t>(slot));
        if (!pObject || !pObject->m_pEntity) return nullptr;
        RwMatrix matrix = pObject->m_pEntity->GetMatrix().ToRwMatrix();
        CVector rotation;
        pObject->GetRotation(&rotation.x, &rotation.y, &rotation.z);
        values[1] = matrix.pos.x;
        values[2] = matrix.pos.y;
        values[3] = matrix.pos.z;
        values[4] = rotation.x;
        values[5] = rotation.y;
        values[6] = rotation.z;
    } else {
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(10);
    if (result == nullptr) return nullptr;
    env->SetFloatArrayRegion(result, 0, 10, values);
    return result;
}

void CObjectEditor::SendOnEditAttach(int response, int index, int modelid, int bone, CVector offset, CVector rot, CVector scale){

    RakNet::BitStream bsSend;

    bsSend.Write((uint32_t)response);
    bsSend.Write((uint32_t)index);
    bsSend.Write((uint32_t)modelid);
    bsSend.Write((uint32_t)bone);
    bsSend.Write(offset);
    bsSend.Write(rot);
    bsSend.Write(scale);
    bsSend.Write((uint32_t)0);
    bsSend.Write((uint32_t)0);

    pNetGame->GetRakClient()->RPC(&RPC_EditAttachedObject, &bsSend, HIGH_PRIORITY, RELIABLE, 0, false, UNASSIGNED_NETWORK_ID, nullptr);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_samp_mobile_game_ui_AttachEdit_AttachClick(JNIEnv *env, jobject thiz, jint button_type,
                                                  jboolean button_id) {

    auto pPlayer = pNetGame->GetPlayerPool()->GetLocalPlayer()->GetPlayerPed();
    int slot = CObjectEditor::iEditedId;
    auto attach = pPlayer->GetAttachedObject(slot);
    if(CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {

        if (!pNetGame->GetPlayerPool()->GetLocalPlayer()->GetPlayerPed()->GetAttachedObject(
                slot)) {
            CObjectEditor::time = 0;
            pJavaWrapper->HideEditObject();
            return;
        }
    }

    CObject *pObject = nullptr;
    if(CObjectEditor::editType == CObjectEditor::TYPE_OBJECT) {
        pObject = pNetGame->GetObjectPool()->GetAt(CObjectEditor::iEditedId);
        if (!pObject) {
            CObjectEditor::time = 0;
            pJavaWrapper->HideEditObject();
            return;
        }
    }

    if (button_type >= 0 && button_type <= 2) {
        // Property order matches the PC-style X/Y/Z labels and settings read-back.
        const float value = button_id ? 0.006f : -0.006f;
        const int axis = button_type;
        if (CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {
            attach->vecOffset[axis] += value;
        } else if (CObjectEditor::editType == CObjectEditor::TYPE_OBJECT) {
            RwMatrix matrix = pObject->m_pEntity->GetMatrix().ToRwMatrix();
            auto pos = matrix.pos;
            (&pos.x)[axis] += value;
            pObject->m_pEntity->SetPosn(pos.x, pos.y, pos.z);
            pObject->m_pEntity->UpdateRW();
            pObject->m_pEntity->UpdateRwFrame();
        }
    }
    if(button_type == 3) { // scale
        float value = (button_id)?(0.006f):(-0.006f);

        if(CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {
            attach->vecScale.x += value;
            attach->vecScale.y += value;
            attach->vecScale.z += value;
        }
    }
    if(button_type == 4) { // rot x
        float value = (button_id)?(0.1f):(-0.1f);

        if(CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {
            attach->vecRotation.x += value;
        }
        if(CObjectEditor::editType == CObjectEditor::TYPE_OBJECT) {
            float x, y, z;
            pObject->GetRotation(&x, &y, &z);
            x += value;

            if(pObject)
                pObject->InstantRotate(x, y, z);
        }
    }
    if(button_type == 5) { // rot y
        float value = (button_id)?(0.1f):(-0.1f);

        if(CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {
            attach->vecRotation.y += value;
        }
        if(CObjectEditor::editType == CObjectEditor::TYPE_OBJECT) {
            float x, y, z;
            pObject->GetRotation(&x, &y, &z);
            y += value;

            if(pObject)
                pObject->InstantRotate(x, y, z);
        }
    }
    if(button_type == 6) { // rot z
        float value = (button_id)?(1.0f):(-1.0f);

        if(CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {
            attach->vecRotation.z += value;
        }
        if(CObjectEditor::editType == CObjectEditor::TYPE_OBJECT) {

            float x, y, z;
            pObject->GetRotation(&x, &y, &z);
            z += value;

            if(pObject)
                pObject->InstantRotate(x, y, z);
        }
    }

    if(GetTickCount() - CObjectEditor::time > 250) {
        CObjectEditor::time = GetTickCount();

        if (CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {
            CObjectEditor::SendOnEditAttach(
                    2,
                    slot,
                    attach->dwModelId,
                    attach->dwSampBone,
                    attach->vecOffset,
                    attach->vecRotation,
                    attach->vecScale
            );
        }
        if (CObjectEditor::editType == CObjectEditor::TYPE_OBJECT) {
            auto pObject = pNetGame->GetObjectPool()->GetAt(CObjectEditor::iEditedId);

            RwMatrix matrix4X4 = pObject->m_pEntity->GetMatrix().ToRwMatrix();
            auto pos = matrix4X4.pos;
            CVector rot;
            pObject->GetRotation(&rot.x, &rot.y, &rot.z);

            RakNet::BitStream bsSend;

            bsSend.Write((bool) CObjectEditor::bPlayerObj);
            bsSend.Write((uint16_t) CObjectEditor::iEditedId);
            bsSend.Write((uint32_t) 2);
            bsSend.Write((float) pos.x);
            bsSend.Write((float) pos.y);
            bsSend.Write((float) pos.z);
            bsSend.Write((float) rot.x);
            bsSend.Write((float) rot.y);
            bsSend.Write((float) rot.z);

            pNetGame->GetRakClient()->RPC(&RPC_EditObject, &bsSend, HIGH_PRIORITY, RELIABLE, 0,
                                          false, UNASSIGNED_NETWORK_ID, nullptr);
        }
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_samp_mobile_game_ui_AttachEdit_Save(JNIEnv *env, jobject thiz) {
    if (!pNetGame || CObjectEditor::iEditedId == INVALID_EDITED_SLOT) {
        resetObjectEditorState();
        if (pJavaWrapper) pJavaWrapper->HideEditObject();
        return;
    }

    const int slot = CObjectEditor::iEditedId;
    if (CObjectEditor::editType == CObjectEditor::TYPE_PLAYER_ATTACH) {
        CPlayerPed* player = pNetGame->GetPlayerPool()
                ? pNetGame->GetPlayerPool()->GetLocalPlayer()->GetPlayerPed() : nullptr;
        auto attach = player ? player->GetAttachedObject(slot) : nullptr;
        if (attach) {
            CObjectEditor::SendOnEditAttach(
                    CObjectEditor::EDIT_RESPONSE_FINAL,
                    slot,
                    attach->dwModelId,
                    attach->dwSampBone,
                    attach->vecOffset,
                    attach->vecRotation,
                    attach->vecScale);
        }
    } else if (CObjectEditor::editType == CObjectEditor::TYPE_OBJECT) {
        auto pObject = pNetGame->GetObjectPool()
                ? pNetGame->GetObjectPool()->GetAt(static_cast<uint16_t>(slot)) : nullptr;
        if (pObject && pObject->m_pEntity) {
            RwMatrix matrix = pObject->m_pEntity->GetMatrix().ToRwMatrix();
            auto pos = matrix.pos;
            CVector rot;
            pObject->GetRotation(&rot.x, &rot.y, &rot.z);

            RakNet::BitStream bsSend;
            bsSend.Write((bool) CObjectEditor::bPlayerObj);
            bsSend.Write((uint16_t) slot);
            bsSend.Write((uint32_t) CObjectEditor::EDIT_RESPONSE_FINAL);
            bsSend.Write((float) pos.x);
            bsSend.Write((float) pos.y);
            bsSend.Write((float) pos.z);
            bsSend.Write((float) rot.x);
            bsSend.Write((float) rot.y);
            bsSend.Write((float) rot.z);
            pNetGame->GetRakClient()->RPC(&RPC_EditObject, &bsSend, HIGH_PRIORITY, RELIABLE,
                                          0, false, UNASSIGNED_NETWORK_ID, nullptr);
        }
    }

    resetObjectEditorState();
    if (pJavaWrapper) pJavaWrapper->HideEditObject();
}
